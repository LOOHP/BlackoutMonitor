package com.loohp.backoutmonitors;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

public class BlackoutMonitors {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);

    private static final int TIME_FONT_SIZE = 190;
    private static final int DATE_FONT_SIZE = 40;

    private static final int BURN_IN_MAX_SHIFT_PX = 20;
    private static final int BURN_IN_INTERVAL_MS = (int) Duration.ofMinutes(15).toMillis();

    private static final float DIM_FACTOR = 0.3F;

    private static final Random RANDOM = new Random();

    private final List<JFrame> frames = new ArrayList<>();
    private final List<JLabel> timeLabels = new ArrayList<>();
    private final List<JLabel> dateLabels = new ArrayList<>();

    private int burnInDx = 0;
    private int burnInDy = 0;

    private Robot robot;
    private Point baseMousePos;
    private int mouseStep = 0;

    private Font timeFont;
    private Font dateFont;

    private volatile boolean focusExitArmed = false;
    private Timer focusExitTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BlackoutMonitors().start());
    }

    private void start() {
        Cursor invisibleCursor = createInvisibleCursor();

        timeFont = loadGoogleSans(Font.PLAIN, TIME_FONT_SIZE);
        dateFont = loadGoogleSans(Font.PLAIN, DATE_FONT_SIZE);

        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();

        for (GraphicsDevice device : devices) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();

            JFrame frame = new JFrame(device.getDefaultConfiguration());
            frame.setUndecorated(true);
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

            JPanel content = new JPanel(null);
            content.setBackground(Color.BLACK);
            frame.setContentPane(content);

            JLabel timeLabel = new JLabel("", SwingConstants.CENTER);
            JLabel dateLabel = new JLabel("", SwingConstants.CENTER);

            content.add(timeLabel);
            content.add(dateLabel);

            frame.setBounds(bounds);
            frame.setCursor(invisibleCursor);

            bindEscToQuit(frame);

            frames.add(frame);
            timeLabels.add(timeLabel);
            dateLabels.add(dateLabel);

            closeOnFocusLoss(frame);
        }

        for (JFrame f : frames) {
            f.setVisible(true);
        }

        layoutLabels();
        startBurnInTimer();
        startClockTimer();

        if (!frames.isEmpty()) {
            frames.getFirst().toFront();
            frames.getFirst().requestFocus();
        }

        try {
            robot = new Robot();
            baseMousePos = MouseInfo.getPointerInfo().getLocation();
            startMouseJiggleTimer();
        } catch (AWTException e) {
            e.printStackTrace();
        }

        new Timer(500, e -> focusExitArmed = true) {{
            setRepeats(false);
        }}.start();
    }

    private Font loadGoogleSans(int style, float size) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("GoogleSans-Regular.ttf")) {
            Font base = Font.createFont(Font.TRUETYPE_FONT, inputStream);
            return base.deriveFont(style, size);
        } catch (Exception e) {
            e.printStackTrace();
            return new Font("SansSerif", style, (int) size); // fallback
        }
    }

    private void layoutLabels() {
        for (int i = 0; i < frames.size(); i++) {
            JFrame frame = frames.get(i);
            JLabel timeLabel = timeLabels.get(i);
            JLabel dateLabel = dateLabels.get(i);

            int w = frame.getContentPane().getWidth();
            int h = frame.getContentPane().getHeight();

            timeLabel.setFont(timeFont);
            dateLabel.setFont(dateFont);

            timeLabel.setForeground(dim(Color.WHITE));
            dateLabel.setForeground(dim(Color.WHITE));

            int totalHeight = TIME_FONT_SIZE + DATE_FONT_SIZE + 20;
            int centerY = (int) (h * (1.0 / 4.0));

            int timeHeight = TIME_FONT_SIZE + 10;
            int dateHeight = DATE_FONT_SIZE + 10;

            timeLabel.setBounds(burnInDx, centerY - totalHeight / 2 + burnInDy, w, timeHeight);
            dateLabel.setBounds(burnInDx, centerY - totalHeight / 2 + burnInDy + timeHeight, w, dateHeight);
        }
    }

    private void startClockTimer() {
        updateClockText();
        Timer timer = new Timer(1000, e -> updateClockText());
        timer.start();
    }

    private void updateClockText() {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(TIME_FMT);
        String date = now.format(DATE_FMT);

        for (JLabel l : timeLabels) {
            l.setText(time);
        }
        for (JLabel l : dateLabels) {
            l.setText(date);
        }
    }

    private void startBurnInTimer() {
        updateBurnInOffsetAndRelayout();

        Timer t = new Timer(BURN_IN_INTERVAL_MS, e -> updateBurnInOffsetAndRelayout());
        t.setRepeats(true);
        t.start();
    }

    private void updateBurnInOffsetAndRelayout() {
        burnInDx = RANDOM.nextInt(BURN_IN_MAX_SHIFT_PX * 2 + 1) - BURN_IN_MAX_SHIFT_PX;
        burnInDy = RANDOM.nextInt(BURN_IN_MAX_SHIFT_PX * 2 + 1) - BURN_IN_MAX_SHIFT_PX;

        layoutLabels();
    }

    private void bindEscToQuit(JFrame frame) {
        JRootPane root = frame.getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke("ESCAPE"), "quit");
        am.put("quit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                quit();
            }
        });
    }

    private void quit() {
        for (JFrame f : frames) {
            try {
                f.dispose();
            } catch (Exception ignored) {
            }
        }
        System.exit(0);
    }

    private Cursor createInvisibleCursor() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image image = toolkit.createImage(new byte[0]);
        return toolkit.createCustomCursor(image, new Point(0, 0), "invisible");
    }

    private static Color dim(Color c) {
        return new Color(Math.round(c.getRed() * DIM_FACTOR), Math.round(c.getGreen() * DIM_FACTOR), Math.round(c.getBlue() * DIM_FACTOR));
    }

    private void startMouseJiggleTimer() {
        Timer t = new Timer(1000, e -> moveMouseSlightly());
        t.setRepeats(true);
        t.start();
    }

    private void moveMouseSlightly() {
        if (robot == null || baseMousePos == null) {
            return;
        }

        int dx = (mouseStep % 2 == 0) ? 1 : -1;
        mouseStep++;

        int x = baseMousePos.x + dx;
        int y = baseMousePos.y;

        robot.mouseMove(x, y);
    }

    private void closeOnFocusLoss(JFrame frame) {
        frame.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                if (!focusExitArmed) {
                    return;
                }
                if (focusExitTimer != null && focusExitTimer.isRunning()) {
                    focusExitTimer.stop();
                }
                focusExitTimer = new Timer(200, evt -> quit());
                focusExitTimer.setRepeats(false);
                focusExitTimer.start();
            }

            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (focusExitTimer != null && focusExitTimer.isRunning()) {
                    focusExitTimer.stop();
                }
            }
        });
    }
}