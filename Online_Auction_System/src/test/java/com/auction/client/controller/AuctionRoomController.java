package com.auction.client.controller;
import com.auction.client.network.AuctionClient;
import com.auction.client.network.ResponseListener;
import com.auction.client.session.UserSession;
import com.auction.client.util.SceneNavigator;
import com.auction.common.dto.ActionType;
import com.auction.common.dto.AuctionStateDTO;
import com.auction.common.dto.BidMessage;
import com.auction.common.dto.Request;
import com.auction.common.dto.Response;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Duration;

/**
 * Controller của màn hình phòng đấu giá realtime (auction_room.fxml).
 *
 * <p>Luồng làm việc:
 * <ol>
 *   <li>SceneNavigator gọi {@link #enterRoom(String)} sau khi load FXML.</li>
 *   <li>Controller gửi JOIN_AUCTION, server trả AUCTION_STATE để dựng toàn bộ giao diện.</li>
 *   <li>Các sự kiện NEW_BID, AUCTION_EXTENDED, AUCTION_ENDED được server đẩy xuống
 *       qua {@link AuctionClient}; mọi thay đổi giao diện đều chạy trong Platform.runLater.</li>
 *   <li>Khi rời phòng phải gọi {@link #dispose()} (nút Quay lại đã gọi sẵn).</li>
 * </ol>
 */
public class AuctionRoomController implements ResponseListener {

    private static final NumberFormat MONEY = NumberFormat.getInstance(
            Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_CHART_POINTS = 50;
    private static final long URGENT_MILLIS = 30_000L;
    private static final String STYLE_ERROR = "error";
    private static final String STYLE_INFO = "info";
    private static final String STYLE_SUCCESS = "success";

    static {
        MONEY.setMaximumFractionDigits(0);
    }

    // ===== FXML =====
    @FXML private Label itemNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label timerLabel;
    @FXML private Label itemDetailsLabel;
    @FXML private Label startPriceLabel;
    @FXML private Label minIncrementLabel;
    @FXML private Label sellerLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label leaderLabel;
    @FXML private Label resultBanner;
    @FXML private Label balanceLabel;
    @FXML private Label messageLabel;
    @FXML private TextField bidField;
    @FXML private Button bidButton;
    @FXML private LineChart<Number, Number> priceChart;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private TableView<BidRow> bidTable;
    @FXML private TableColumn<BidRow, String> timeCol;
    @FXML private TableColumn<BidRow, String> bidderCol;
    @FXML private TableColumn<BidRow, String> amountCol;

    // ===== Dữ liệu dùng chung =====
    private final UserSession session = UserSession.getInstance();
    private final AuctionClient client = session.getClient();
    private final ObservableList<BidRow> rows = FXCollections.observableArrayList();
    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();
    private final Set<String> seenBidIds = new HashSet<>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auction-room-sender");
        t.setDaemon(true);
        return t;
    });

    // ===== Trạng thái phòng =====
    private String auctionId;
    private double startingPrice;
    private double minIncrement;
    private double currentPrice;
    private int bidCount;
    private String leaderId;
    private String leaderName;
    private long endTimeMillis;
    private long clockOffset;
    private boolean roomOpen;
    private boolean ended;
    private boolean timeUp;
    private boolean connected = true;
    private boolean sending;
    private boolean disposed;
    private Timeline countdown;

    // =====================================================================
    // Khởi tạo và vào / rời phòng
    // =====================================================================

    @FXML
    private void initialize() {
        timeCol.setCellValueFactory(c -> c.getValue().timeProperty());
        bidderCol.setCellValueFactory(c -> c.getValue().bidderProperty());
        amountCol.setCellValueFactory(c -> c.getValue().amountProperty());
        bidTable.setItems(rows);
        bidTable.setPlaceholder(new Label("Chưa có lượt đặt giá nào"));
        bidTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(BidRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("my-bid");
                if (!empty && item != null && item.isMine()) {
                    getStyleClass().add("my-bid");
                }
            }
        });

        priceChart.setAnimated(false);
        priceChart.setLegendVisible(false);
        series.setName("Giá");
        priceChart.getData().add(series);
        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);

        bidField.setOnAction(e -> onPlaceBid());
        resultBanner.setManaged(false);
        resultBanner.setVisible(false);
        balanceLabel.setText("Số dư: " + money(session.getBalance()));
        refreshBidControls();
    }

    /**
     * Vào một phòng đấu giá. Gọi ngay sau khi load FXML.
     *
     * @param id mã phiên đấu giá
     */
    public void enterRoom(String id) {
        this.auctionId = id;
        client.addListener(this);
        sendAsync(new Request(ActionType.JOIN_AUCTION, id));
        if (!canBid()) {
            setMessage("Chỉ tài khoản Bidder mới được đặt giá.", STYLE_INFO);
        }
    }

    /** Giải phóng tài nguyên khi rời phòng. Có thể gọi nhiều lần. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        stopCountdown();
        client.removeListener(this);
        if (auctionId != null) {
            sendAsync(new Request(ActionType.LEAVE_AUCTION, auctionId));
        }
        sender.shutdown();
    }

    @FXML
    private void onBack() {
        dispose();
        SceneNavigator.goTo("dashboard.fxml");
    }

    // =====================================================================
    // Nhận dữ liệu từ server (ResponseListener)
    // =====================================================================

    @Override
    public void onResponse(Response response) {
        // Được gọi từ luồng nền của AuctionClient -> chuyển sang luồng JavaFX.
        Platform.runLater(() -> dispatch(response));
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            connected = false;
            setMessage("Mất kết nối tới máy chủ.", STYLE_ERROR);
            refreshBidControls();
        });
    }

    private void dispatch(Response r) {
        if (disposed) {
            return;
        }
        ActionType action = r.getAction();
        if (!r.isSuccess()) {
            if (action == ActionType.PLACE_BID || action == ActionType.JOIN_AUCTION
                    || action == ActionType.ERROR) {
                setMessage(r.getMessage(), STYLE_ERROR);
            }
            return;
        }
        Object data = r.getData();
        switch (action) {
            case AUCTION_STATE -> {
                if (data instanceof AuctionStateDTO state) {
                    renderState(state);
                }
            }
            case NEW_BID -> {
                if (data instanceof BidMessage bid) {
                    applyBid(bid);
                }
            }
            case AUCTION_EXTENDED -> {
                if (data instanceof Long newEnd) {
                    applyExtension(newEnd);
                }
            }
            case AUCTION_ENDED -> applyEnded(data instanceof BidMessage w ? w : null);
            default -> { }
        }
    }

    // =====================================================================
    // Xử lý từng loại sự kiện
    // =====================================================================

    /** Dựng toàn bộ giao diện từ trạng thái đầy đủ của phiên (sau JOIN_AUCTION). */
    private void renderState(AuctionStateDTO s) {
        if (!s.getAuctionId().equals(auctionId)) {
            return;
        }
        itemNameLabel.setText(s.getItemName());
        itemDetailsLabel.setText(s.getItemDetails());
        sellerLabel.setText("Người bán: " + s.getSellerName());
        startingPrice = s.getStartingPrice();
        minIncrement = s.getMinIncrement();
        startPriceLabel.setText("Giá khởi điểm: " + money(startingPrice));
        minIncrementLabel.setText(minIncrement > 0
                ? "Bước giá tối thiểu: " + money(minIncrement) : "");

        // Reset dữ liệu cũ rồi nạp lại lịch sử (cũ -> mới).
        rows.clear();
        series.getData().clear();
        seenBidIds.clear();
        bidCount = 0;
        leaderId = null;
        leaderName = null;
        currentPrice = startingPrice;

        List<BidMessage> history = new ArrayList<>(s.getBidHistory());
        history.sort(Comparator.comparingLong(BidMessage::getTimestampMillis));
        history.forEach(b -> addBid(b, false));
        refreshPriceViews(false);

        endTimeMillis = s.getEndTimeMillis();
        clockOffset = s.getServerNowMillis() - System.currentTimeMillis();
        timeUp = false;
        ended = false;
        applyStatus(s.getStatus());
        if (!ended) {
            startCountdown();
        }
        refreshBidControls();
    }

    /** Có lượt đặt giá mới (NEW_BID). */
    private void applyBid(BidMessage bid) {
        if (!bid.getAuctionId().equals(auctionId)) {
            return;
        }
        if (bid.getBidId() != null && seenBidIds.contains(bid.getBidId())) {
            return; // đã có trong lịch sử lúc JOIN
        }
        boolean wasLeader = isMe(leaderId);
        addBid(bid, true);
        refreshPriceViews(true);

        if (isMe(bid.getBidderId())) {
            setMessage("Đặt giá thành công: " + money(bid.getAmount()), STYLE_SUCCESS);
        } else if (wasLeader) {
            setMessage("Bạn vừa bị " + bid.getBidderName() + " vượt giá!", STYLE_ERROR);
        }
    }

    /** Anti-sniping: server gia hạn, gửi thời điểm kết thúc mới. */
    private void applyExtension(long newEndMillis) {
        long extraSeconds = Math.max(0, (newEndMillis - endTimeMillis) / 1000);
        endTimeMillis = newEndMillis; // Timeline đọc lại biến này ở mỗi lần tick
        setMessage("Phiên được gia hạn thêm " + extraSeconds + " giây.", STYLE_INFO);
        flashTimer();
    }

    /** Phiên kết thúc (AUCTION_ENDED). winner có thể null nếu không ai đặt giá. */
    private void applyEnded(BidMessage winner) {
        ended = true;
        roomOpen = false;
        stopCountdown();
        timerLabel.setText("Hết giờ");
        statusLabel.setText("FINISHED");
        if (winner == null) {
            showResult("Phiên kết thúc, không có người thắng.");
        } else if (isMe(winner.getBidderId())) {
            showResult("Chúc mừng! Bạn đã thắng với giá " + money(winner.getAmount()));
        } else {
            showResult("Người thắng: " + winner.getBidderName()
                    + " - " + money(winner.getAmount()));
        }
        refreshBidControls();
    }

    private void applyStatus(String status) {
        statusLabel.setText(status);
        statusLabel.getStyleClass().removeIf(c -> c.startsWith("status-"));
        statusLabel.getStyleClass().add("status-" + status.toLowerCase());

        roomOpen = "OPEN".equals(status) || "RUNNING".equals(status);
        if ("FINISHED".equals(status) || "PAID".equals(status)) {
            ended = true;
            stopCountdown();
            timerLabel.setText("Hết giờ");
            showResult(leaderName == null
                    ? "Phiên đã kết thúc, không có người thắng."
                    : "Phiên đã kết thúc. Người thắng: " + leaderName
                    + " - " + money(currentPrice));
        } else if ("CANCELED".equals(status)) {
            ended = true;
            stopCountdown();
            timerLabel.setText("--:--:--");
            showResult("Phiên đã bị hủy.");
        }
    }

    // =====================================================================
    // Đặt giá
    // =====================================================================

    @FXML
    private void onPlaceBid() {
        if (bidButton.isDisabled()) {
            return;
        }
        double amount;
        try {
            amount = parseAmount(bidField.getText());
        } catch (NumberFormatException e) {
            setMessage("Giá không hợp lệ.", STYLE_ERROR);
            return;
        }
        double min = minNextBid();
        if (amount < min) {
            setMessage("Giá tối thiểu là " + money(min) + ".", STYLE_ERROR);
            return;
        }
        if (amount > session.getBalance()) {
            setMessage("Số dư không đủ để đặt giá này.", STYLE_ERROR);
            return;
        }

        BidMessage bid = new BidMessage(null, auctionId, session.getUserId(),
                session.getFullName(), amount, 0L);
        sendAsync(new Request(ActionType.PLACE_BID, bid));
        bidField.clear();
        setMessage("Đang gửi giá " + money(amount) + "...", STYLE_INFO);

        // Chặn bấm liên tiếp trong thời gian ngắn.
        sending = true;
        refreshBidControls();
        PauseTransition unlock = new PauseTransition(Duration.millis(500));
        unlock.setOnFinished(e -> {
            sending = false;
            refreshBidControls();
        });
        unlock.play();
    }

    /** Giá thấp nhất được phép đặt ở thời điểm hiện tại (server vẫn kiểm tra lại). */
    private double minNextBid() {
        if (bidCount == 0) {
            return startingPrice;
        }
        return minIncrement > 0 ? currentPrice + minIncrement : currentPrice + 1;
    }

    private static double parseAmount(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new NumberFormatException("empty");
        }
        return Double.parseDouble(digits);
    }

    // =====================================================================
    // Cập nhật giao diện
    // =====================================================================

    /** Thêm một bid vào dữ liệu: bảng, biểu đồ, giá hiện tại, người dẫn đầu. */
    private void addBid(BidMessage b, boolean newestFirstAnimated) {
        if (b.getBidId() != null) {
            seenBidIds.add(b.getBidId());
        }
        bidCount++;
        currentPrice = b.getAmount();
        leaderId = b.getBidderId();
        leaderName = b.getBidderName();

        rows.add(0, new BidRow(
                Instant.ofEpochMilli(b.getTimestampMillis())
                        .atZone(ZoneId.systemDefault()).format(TIME_FMT),
                b.getBidderName(),
                money(b.getAmount()),
                isMe(b.getBidderId())));

        // Trục X dùng bộ đếm riêng vì danh sách điểm bị cắt còn 50 điểm cuối.
        series.getData().add(new XYChart.Data<>(bidCount, b.getAmount()));
        while (series.getData().size() > MAX_CHART_POINTS) {
            series.getData().remove(0);
        }
    }

    private void refreshPriceViews(boolean flash) {
        currentPriceLabel.setText(money(currentPrice));
        if (leaderName == null) {
            leaderLabel.setText("Chưa có người đặt giá");
        } else if (isMe(leaderId)) {
            leaderLabel.setText("Bạn đang dẫn đầu");
        } else {
            leaderLabel.setText("Dẫn đầu: " + leaderName);
        }
        toggleStyle(leaderLabel, "leader-me", isMe(leaderId));
        bidField.setPromptText("Tối thiểu " + money(minNextBid()));
        if (flash) {
            toggleStyle(currentPriceLabel, "price-up", true);
            PauseTransition p = new PauseTransition(Duration.millis(600));
            p.setOnFinished(e -> toggleStyle(currentPriceLabel, "price-up", false));
            p.play();
        }
    }

    private void showResult(String text) {
        resultBanner.setText(text);
        resultBanner.setManaged(true);
        resultBanner.setVisible(true);
    }

    private void refreshBidControls() {
        boolean allowed = connected && roomOpen && !ended && !timeUp && canBid() && !sending;
        bidButton.setDisable(!allowed);
        bidField.setDisable(!allowed);
    }

    private void setMessage(String text, String styleClass) {
        messageLabel.setText(text == null ? "" : text);
        messageLabel.getStyleClass().removeAll(STYLE_ERROR, STYLE_INFO, STYLE_SUCCESS);
        messageLabel.getStyleClass().add(styleClass);
    }

    private static void toggleStyle(Node node, String styleClass, boolean on) {
        node.getStyleClass().remove(styleClass);
        if (on) {
            node.getStyleClass().add(styleClass);
        }
    }

    // =====================================================================
    // Đồng hồ đếm ngược
    // =====================================================================

    private void startCountdown() {
        stopCountdown();
        countdown = new Timeline(new KeyFrame(Duration.millis(250), e -> tick()));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
        tick();
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

    private void tick() {
        // Dùng giờ server (cộng độ lệch) để các máy hiển thị giống nhau.
        long left = endTimeMillis - (System.currentTimeMillis() + clockOffset);
        boolean up = left <= 0;
        timerLabel.setText(up ? "Hết giờ" : formatHms(left));
        toggleStyle(timerLabel, "timer-urgent", !up && left < URGENT_MILLIS);
        if (up != timeUp) {
            // Không dừng Timeline: nếu server gia hạn muộn thì phòng tự mở lại.
            timeUp = up;
            refreshBidControls();
        }
    }

    private void flashTimer() {
        FadeTransition ft = new FadeTransition(Duration.millis(250), timerLabel);
        ft.setFromValue(1.0);
        ft.setToValue(0.2);
        ft.setCycleCount(6);
        ft.setAutoReverse(true);
        ft.setOnFinished(e -> timerLabel.setOpacity(1.0));
        ft.play();
    }

    private static String formatHms(long millis) {
        long totalSeconds = (millis + 999) / 1000;
        return String.format("%02d:%02d:%02d",
                totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60);
    }

    // =====================================================================
    // Gửi mạng (không chạy trên luồng JavaFX để giao diện không bị đơ)
    // =====================================================================

    private void sendAsync(Request request) {
        try {
            sender.submit(() -> {
                try {
                    client.send(request);
                } catch (IOException e) {
                    Platform.runLater(this::onSendFailed);
                }
            });
        } catch (RejectedExecutionException e) {
            // Controller đã dispose, bỏ qua.
        }
    }

    private void onSendFailed() {
        connected = false;
        setMessage("Không gửi được yêu cầu, hãy kiểm tra kết nối.", STYLE_ERROR);
        refreshBidControls();
    }

    // =====================================================================
    // Tiện ích
    // =====================================================================

    private boolean canBid() {
        return "BIDDER".equals(session.getRole());
    }

    private boolean isMe(String userId) {
        return userId != null && userId.equals(session.getUserId());
    }

    private static String money(double value) {
        return MONEY.format(value) + " đ";
    }

    /** Một dòng trong bảng lịch sử đặt giá. */
    public static final class BidRow {
        private final StringProperty time = new SimpleStringProperty();
        private final StringProperty bidder = new SimpleStringProperty();
        private final StringProperty amount = new SimpleStringProperty();
        private final boolean mine;

        /** Tạo một dòng lịch sử. */
        public BidRow(String time, String bidder, String amount, boolean mine) {
            this.time.set(time);
            this.bidder.set(bidder);
            this.amount.set(amount);
            this.mine = mine;
        }

        public StringProperty timeProperty() {
            return time;
        }

        public StringProperty bidderProperty() {
            return bidder;
        }

        public StringProperty amountProperty() {
            return amount;
        }

        public boolean isMine() {
            return mine;
        }
    }
}