public abstract class User {
    private String id;
    private String username;
    private String password;
    private String fullName;
    private String email;
    private Role role;

    public User(String id, String username, String password, String fullName, String email, Role role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullname() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean authenticate(String password) {
        return this.password != null && this.password.equals(password);
    }

    public abstract void displayInfo();
}
