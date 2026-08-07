package mc.jazhdo;

public class BroadcastEvent {
    private final String name, msg;

    public BroadcastEvent(String name, String msg) {
        this.name = name;
        this.msg = msg;
    }

    public String getName() {
        return name;
    }

    public String getMsg() {
        return msg;
    }
}
