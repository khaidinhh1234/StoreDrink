package utils;

import java.util.concurrent.CopyOnWriteArrayList;

/** Small in-process event bus for notifying screens after successful data writes. */
public final class DataChangeNotifier {
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private DataChangeNotifier() { }

    public static void addListener(Runnable listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void publish() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                System.err.println("Không thể thông báo dữ liệu mới: " + exception.getMessage());
            }
        }
    }
}
