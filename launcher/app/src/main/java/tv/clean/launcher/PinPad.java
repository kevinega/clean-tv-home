package tv.clean.launcher;

import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A 4-digit PIN entered with the remote: Up/Down change the digit, Right or OK moves on,
 * Left goes back, Back cancels. Number keys work too, where the remote has them.
 */
class PinPad extends FrameLayout {
    interface Done {
        void onPin(String pin);
    }

    private final HomeActivity a;
    private final TextView title, hint;
    private final TextView[] boxes = new TextView[4];
    private final LinearLayout row;
    private final int[] digits = new int[4];
    private int pos;
    private Done done;

    PinPad(HomeActivity a) {
        super(a);
        this.a = a;
        setBackgroundColor(0xD9000000);
        setVisibility(GONE);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(a.px(40), a.px(32), a.px(40), a.px(28));
        panel.setBackground(a.round(0xF22A2A2D, a.px(28), 0x33FFFFFF));
        addView(panel, new LayoutParams(-2, -2, Gravity.CENTER));

        title = a.text(24, a.medium, Color.WHITE);
        panel.addView(title);

        row = new LinearLayout(a);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = a.px(24);
        rlp.bottomMargin = a.px(20);
        panel.addView(row, rlp);
        for (int i = 0; i < 4; i++) {
            TextView b = a.text(34, a.light, Color.WHITE);
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.px(64), a.px(76));
            lp.leftMargin = i == 0 ? 0 : a.px(14);
            row.addView(b, lp);
            boxes[i] = b;
        }

        hint = a.text(15, a.regular, 0x99FFFFFF);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint);
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    void open(String heading, Done done) {
        this.done = done;
        title.setText(heading);
        reset("▲ ▼ change number   ·   OK next   ·   Back cancel");
        setVisibility(VISIBLE);
    }

    void close() {
        setVisibility(GONE);
    }

    /** Wrong PIN: shake, say why, start over. */
    void fail(String why) {
        reset(why);
        hint.setTextColor(0xFFFF8A80);
        row.animate().cancel();
        row.setTranslationX(0);
        row.animate().translationX(a.px(14)).setDuration(60).withEndAction(() ->
                row.animate().translationX(-a.px(14)).setDuration(90).withEndAction(() ->
                        row.animate().translationX(0).setDuration(60).start()).start()).start();
    }

    private void reset(String hintText) {
        pos = 0;
        for (int i = 0; i < 4; i++) digits[i] = 0;
        hint.setText(hintText);
        hint.setTextColor(0x99FFFFFF);
        render();
    }

    /** Returns false for keys the pad does not use (volume and the like), so they still work. */
    boolean onKey(KeyEvent e) {
        int k = e.getKeyCode();
        boolean number = k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean used = number || ok || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_UP
                || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT
                || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!used) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;

        if (k == KeyEvent.KEYCODE_BACK) close();
        else if (number) {
            digits[pos] = k - KeyEvent.KEYCODE_0;
            next();
        } else if (k == KeyEvent.KEYCODE_DPAD_UP) digits[pos] = (digits[pos] + 1) % 10;
        else if (k == KeyEvent.KEYCODE_DPAD_DOWN) digits[pos] = (digits[pos] + 9) % 10;
        else if (k == KeyEvent.KEYCODE_DPAD_LEFT && pos > 0) pos--;
        else if ((ok && e.getRepeatCount() == 0) || k == KeyEvent.KEYCODE_DPAD_RIGHT) next();
        render();
        return true;
    }

    private void next() {
        if (pos < 3) {
            pos++;
            digits[pos] = 0;
            return;
        }
        StringBuilder pin = new StringBuilder();
        for (int d : digits) pin.append(d);
        if (done != null) done.onPin(pin.toString());
    }

    private void render() {
        for (int i = 0; i < 4; i++) {
            TextView b = boxes[i];
            boolean cur = i == pos;
            b.setText(i < pos ? "•" : cur ? String.valueOf(digits[i]) : "");
            b.setBackground(a.round(cur ? 0x38FFFFFF : 0x14FFFFFF, a.px(14), cur ? 0xCCFFFFFF : 0x26FFFFFF));
        }
    }
}
