package com.auction.client.network;

import com.auction.common.dto.ActionType;
import com.auction.common.dto.Request;
import com.auction.common.dto.Response;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Socket client dùng chung cho toàn bộ giao diện (TV3 và TV4).
 *
 * <p>Một instance duy nhất được giữ trong {@code UserSession}. Class này:
 * <ul>
 *   <li>mở kết nối tới server và chạy một luồng nền để nghe mọi {@link Response};</li>
 *   <li>cho phép nhiều controller đăng ký / hủy nhận thông điệp qua
 *       {@link ResponseListener};</li>
 *   <li>gửi {@link Request} an toàn khi nhiều luồng cùng gọi;</li>
 *   <li>báo {@link ResponseListener#onDisconnected()} khi mất kết nối.</li>
 * </ul>
 *
 * <p><b>Lưu ý luồng:</b> listener được gọi trên luồng nền, KHÔNG phải luồng JavaFX.
 * Controller phải tự bọc thao tác giao diện bằng {@code Platform.runLater}.
 * Không gọi {@link #send(Request)} hoặc {@link #connect(String, int)} trực tiếp
 * trên luồng JavaFX nếu mạng có thể chậm.
 */
public class AuctionClient {

    private static final Logger LOG = Logger.getLogger(AuctionClient.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final long DEFAULT_REQUEST_TIMEOUT_SECONDS = 10L;

    private final CopyOnWriteArrayList<ResponseListener> listeners =
            new CopyOnWriteArrayList<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    private Socket socket;
    private ObjectOutputStream out;
    private Thread listenerThread;

    // =====================================================================
    // Kết nối
    // =====================================================================

    /**
     * Kết nối tới server và bắt đầu nghe.
     *
     * @param host địa chỉ server
     * @param port cổng server
     * @throws IOException nếu không kết nối được hoặc đã kết nối rồi
     */
    public synchronized void connect(String host, int port) throws IOException {
        if (connected.get()) {
            throw new IOException("Đã kết nối tới server.");
        }
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);

            // QUAN TRỌNG: tạo ObjectOutputStream trước và flush, rồi mới tạo
            // ObjectInputStream. Server cũng phải làm đúng thứ tự này, nếu không
            // hai bên sẽ chờ header của nhau và bị treo.
            ObjectOutputStream o = new ObjectOutputStream(s.getOutputStream());
            o.flush();
            ObjectInputStream i = new ObjectInputStream(s.getInputStream());

            this.socket = s;
            this.out = o;
            connected.set(true);

            listenerThread = new Thread(() -> listen(i), "auction-client-listener");
            listenerThread.setDaemon(true); // tắt app thì luồng tự dừng
            listenerThread.start();
        } catch (IOException e) {
            closeQuietly(s);
            throw e;
        }
    }

    /** Ngắt kết nối chủ động (không gọi onDisconnected). */
    public void disconnect() {
        close(false);
    }

    /** Cho biết client đang kết nối hay không. */
    public boolean isConnected() {
        return connected.get();
    }

    // =====================================================================
    // Đăng ký nhận thông điệp
    // =====================================================================

    /** Đăng ký nhận mọi Response từ server. Gọi {@link #removeListener} khi rời màn hình. */
    public void addListener(ResponseListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    /** Hủy đăng ký. */
    public void removeListener(ResponseListener listener) {
        listeners.remove(listener);
    }

    // =====================================================================
    // Gửi
    // =====================================================================

    /**
     * Gửi một request lên server (không chờ phản hồi).
     *
     * @param request thông điệp cần gửi
     * @throws IOException nếu chưa kết nối hoặc ghi lỗi
     */
    public void send(Request request) throws IOException {
        if (!connected.get()) {
            throw new IOException("Chưa kết nối tới server.");
        }
        try {
            // Nhiều luồng có thể gửi cùng lúc nên phải khóa để không lẫn dữ liệu.
            synchronized (writeLock) {
                out.writeObject(request);
                out.flush();
                // reset() để lần sau gửi lại object đã sửa vẫn ghi dữ liệu mới,
                // không bị ObjectOutputStream dùng lại bản cũ trong cache.
                out.reset();
            }
        } catch (IOException e) {
            close(true);
            throw e;
        }
    }

    /**
     * Gửi request rồi chờ phản hồi có action tương ứng (dùng cho đăng nhập, lấy danh sách...).
     *
     * <p>Future hoàn thành khi nhận Response có action nằm trong {@code expected} hoặc
     * action {@link ActionType#ERROR}; hoàn thành lỗi nếu gửi thất bại, mất kết nối hoặc
     * quá thời gian chờ. Đừng gọi {@code get()} trên luồng JavaFX; hãy dùng
     * {@code thenAccept(r -> Platform.runLater(...))}.
     *
     * @param request thông điệp cần gửi
     * @param expected các action của phản hồi mong đợi
     * @return future chứa phản hồi
     */
    public CompletableFuture<Response> request(Request request, ActionType... expected) {
        Set<ActionType> wanted = EnumSet.noneOf(ActionType.class);
        wanted.addAll(List.of(expected));
        wanted.add(ActionType.ERROR);

        CompletableFuture<Response> future = new CompletableFuture<>();
        ResponseListener waiter = new ResponseListener() {
            @Override
            public void onResponse(Response response) {
                if (wanted.contains(response.getAction())) {
                    future.complete(response);
                }
            }

            @Override
            public void onDisconnected() {
                future.completeExceptionally(new IOException("Mất kết nối tới server."));
            }
        };

        listeners.add(waiter);
        future.orTimeout(DEFAULT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> listeners.remove(waiter));

        try {
            send(request);
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    // =====================================================================
    // Luồng nền nghe server
    // =====================================================================

    private void listen(ObjectInputStream in) {
        try {
            while (connected.get()) {
                Object obj = in.readObject();
                if (obj instanceof Response response) {
                    dispatch(response);
                } else {
                    LOG.warning("Bỏ qua thông điệp không phải Response: " + obj);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (connected.get()) {
                LOG.log(Level.INFO, "Mất kết nối tới server: {0}", e.toString());
            }
        } finally {
            close(true);
        }
    }

    private void dispatch(Response response) {
        for (ResponseListener l : listeners) {
            try {
                l.onResponse(response);
            } catch (RuntimeException e) {
                // Lỗi của một controller không được làm chết luồng nghe.
                LOG.log(Level.WARNING, "Listener ném lỗi khi xử lý " + response.getAction(), e);
            }
        }
    }

    // =====================================================================
    // Đóng kết nối
    // =====================================================================

    private void close(boolean notify) {
        if (!connected.compareAndSet(true, false)) {
            return; // đã đóng rồi
        }
        closeQuietly(socket);
        if (notify) {
            for (ResponseListener l : listeners) {
                try {
                    l.onDisconnected();
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Listener ném lỗi trong onDisconnected", e);
                }
            }
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // không còn gì để làm
            }
        }
    }
}