public class Admin extends User {
    private int adminLevel;

    public Admin(String id, String username, String password, String fullName, String email, int adminLevel) {
        super(id, username, password, fullName, email, Role.ADMIN);
        this.adminLevel = adminLevel;
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
    }

    // quản trị hệ thống
    public boolean banUser(User user) {
        if (user != null && user.getRole() != Role.ADMIN) {
            // khóa/cấm tài khoản người dùng khỏi hệ thống
            System.out.println("Admin " + getFullName() + " đã khóa tài khoản: " + user.getUsername());
            return true;
        }
        return false;
    }

    public boolean cancelAuction(Auction auction) {
        if (auction != null) {
            // hủy phiên đấu giá
            System.out.println("Admin " + getFullName() + " đã hủy phiên đấu giá ID: " + auction.getId());
            return true;
        }
        return false;
    }

    public void viewSystemStats() {
        // xem thống kê hệ thống (số lượng người dùng, tổng giao dịch,...)
        System.out.println("--- THỐNG KÊ HỆ THỐNG ---");
        System.out.println("Đang hiển thị báo cáo thống kê cho Quản trị viên cấp độ: " + adminLevel);
    }

    @Override
    public void displayInfo() {
        System.out.println("+) Admin: " + getFullName() + " - Admin Level: " + adminLevel);
    }
}