package view.layout;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import utils.DataChangeNotifier;

/** Refreshes visible screens after a successful API write and when a screen is opened. */
public final class DataSync {
    private static final int CHANGE_DEBOUNCE_MS = 350;
    private static final Map<JComponent, WeakReference<RefreshablePanel>> PANELS = new WeakHashMap<>();
    private static Timer pendingRefresh;
    private static boolean listening;

    private DataSync() { }

    public static synchronized void register(JComponent component, RefreshablePanel panel) {
        PANELS.put(component, new WeakReference<>(panel));
        component.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent event) {
                panel.refreshData();
            }
        });
        if (!listening) {
            DataChangeNotifier.addListener(DataSync::scheduleRefresh);
            listening = true;
        }
    }

    private static void scheduleRefresh() {
        Runnable restart = () -> {
            synchronized (DataSync.class) {
                if (pendingRefresh == null) {
                    pendingRefresh = new Timer(CHANGE_DEBOUNCE_MS, event -> refreshVisiblePanels());
                    pendingRefresh.setRepeats(false);
                }
                pendingRefresh.restart();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) restart.run();
        else SwingUtilities.invokeLater(restart);
    }

    private static void refreshVisiblePanels() {
        ArrayList<Map.Entry<JComponent, WeakReference<RefreshablePanel>>> snapshot;
        synchronized (DataSync.class) {
            snapshot = new ArrayList<>(PANELS.entrySet());
        }
        for (Map.Entry<JComponent, WeakReference<RefreshablePanel>> entry : snapshot) {
            JComponent component = entry.getKey();
            RefreshablePanel panel = entry.getValue().get();
            if (component != null && panel != null && component.isShowing()) {
                panel.refreshData();
            }
        }
    }
}
