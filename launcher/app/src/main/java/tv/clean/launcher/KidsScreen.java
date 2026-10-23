package tv.clean.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Kids Mode: big round tiles that open a YouTube channel, and a "Grown-ups" pill that asks for the PIN. */
class KidsScreen extends FrameLayout {
    private static final int COLUMNS = 4;
    private static final float DISC_SCALE = 1.12f;

    static class Series {
        String name, channel;
        Bitmap art;
        int color;
    }

    private final HomeActivity a;
    private final Runnable onExit;
    private final List<Series> series = new ArrayList<>();
    private final int side, spanW, disc;
    private final ScrollView scroller;
    private final FrameLayout content;
    private final GlassLayer glass;
    private final View glow;
    private int glowColor = 0xFF5E5CE6;
    private ValueAnimator glowAnim;
    private final TextView exit;
    private final RecyclerView rv;

    private boolean onTop;
    private int index;
    private View focused;
    private boolean settled;

    KidsScreen(HomeActivity a, Runnable onExit) {
        super(a);
        this.a = a;
        this.onExit = onExit;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        side = a.px(56);
        spanW = (screenW - 2 * side) / COLUMNS;
        disc = a.px(140);
        setBackgroundColor(Color.BLACK);
        loadSeries();

        int r = a.px(480);
        glow = new View(a);
        LayoutParams glp = new LayoutParams(2 * r, 2 * r);
        glp.leftMargin = screenW / 2 - r;
        glp.topMargin = screenH / 2 + a.px(30) - r;
        addView(glow, glp);
        paintGlow(glowColor);

        scroller = new ScrollView(a);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setFocusable(false);
        scroller.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        addView(scroller, new LayoutParams(-1, -1));

        content = new FrameLayout(a);
        content.setClipChildren(false);
        scroller.addView(content, new ScrollView.LayoutParams(-1, -2));
        glass = new GlassLayer(a);
        content.addView(glass, new LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        content.addView(column, new LayoutParams(-1, -2));

        FrameLayout header = new FrameLayout(a);
        header.setClipChildren(false);
        TextView hello = a.text(30, a.medium, Color.WHITE);
        hello.setText("Hi! What shall we watch?");
        LayoutParams hlp = new LayoutParams(-2, -2);
        hlp.leftMargin = side + a.px(8);
        hlp.topMargin = a.px(38);
        header.addView(hello, hlp);

        exit = a.text(15, a.medium, Color.WHITE);
        exit.setText("Grown-ups");
        exit.setSingleLine();
        exit.setGravity(Gravity.CENTER_VERTICAL);
        exit.setPadding(a.px(14), 0, a.px(18), 0);
        exit.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock, 0, 0, 0);
        exit.setCompoundDrawablePadding(a.px(8));
        styleExit(false);
        LayoutParams elp = new LayoutParams(-2, a.px(40));
        elp.gravity = Gravity.TOP | Gravity.END;
        elp.rightMargin = side;
        elp.topMargin = a.px(40);
        header.addView(exit, elp);
        column.addView(header, new LinearLayout.LayoutParams(-1, a.px(112)));

        rv = new RecyclerView(a);
        rv.setLayoutManager(new GridLayoutManager(a, COLUMNS) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        });
        rv.setItemAnimator(null);
        rv.setClipChildren(false);
        rv.setClipToPadding(false);
        rv.setFocusable(false);
        rv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        rv.setPadding(side, a.px(10), side, 0);
        rv.setAdapter(new Adapter());
        column.addView(rv, new LinearLayout.LayoutParams(-1, -2));
        column.addView(new View(a), new LinearLayout.LayoutParams(-1, screenH));

        content.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!settled && rv.getChildCount() > 0) {
                settled = true;
                setFocus(false, index, false);
            }
        });
    }

    private void loadSeries() {
        for (String item : getResources().getStringArray(R.array.kids_series)) {
            String[] parts = item.split("\\|");
            if (parts.length < 2) continue;
            Series s = new Series();
            s.name = parts[0].trim();
            s.channel = parts[1].trim();
            try (InputStream in = getContext().getAssets().open("kids/" + s.channel + ".jpg")) {
                s.art = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) {
            }
            s.color = s.art == null ? 0xFF5E5CE6
                    : HomeActivity.sampleColor(new BitmapDrawable(getResources(), s.art));
            series.add(s);
        }
    }

    /** Back to the first row, as when Home is pressed. */
    void home() {
        setFocus(false, settled ? index : 0, true);
    }

    boolean onKey(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
        if (ok) {
            if (e.getRepeatCount() > 0) return true;
            if (onTop) onExit.run();
            else if (!series.isEmpty()) open(series.get(index));
            return true;
        }
        if (onTop) {
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && !series.isEmpty()) setFocus(false, index, true);
            return true;
        }
        int i = index, row = i / COLUMNS, col = i % COLUMNS;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) i--;
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && col < COLUMNS - 1 && i + 1 < series.size()) i++;
        else if (k == KeyEvent.KEYCODE_DPAD_UP) {
            if (row == 0) {
                setFocus(true, index, true);
                return true;
            }
            i -= COLUMNS;
        } else if (k == KeyEvent.KEYCODE_DPAD_DOWN && (row + 1) * COLUMNS < series.size()) {
            i = Math.min(i + COLUMNS, series.size() - 1);
        }
        if (i != index) setFocus(false, i, true);
        return true;
    }

    private void setFocus(boolean top, int i, boolean animate) {
        i = Math.max(0, Math.min(i, series.size() - 1));
        View target = top ? exit : rv.getLayoutManager().findViewByPosition(i);
        if (target == null) return;
        if (focused != null && focused != target) style(focused, false);
        onTop = top;
        if (!top) index = i;
        focused = target;
        style(target, true);

        View box = top ? exit : ((ViewGroup) target).getChildAt(0);
        RectF r = rectInContent(box);
        float s = top ? 1.06f : DISC_SCALE;
        float out = a.px(top ? 4 : 7);
        r.inset(-r.width() * (s - 1) / 2 - out, -r.height() * (s - 1) / 2 - out);
        glass.moveTo(r, r.height() / 2, animate);

        int row = top ? 0 : i / COLUMNS;
        int y = row == 0 ? 0 : rv.getTop() + target.getTop() - a.px(40);
        if (animate) scroller.smoothScrollTo(0, y);
        else scroller.scrollTo(0, y);

        if (!top) glowTo(series.get(i).color);
    }

    private void style(View v, boolean on) {
        if (v == exit) {
            styleExit(on);
            exit.animate().scaleX(on ? 1.06f : 1f).scaleY(on ? 1.06f : 1f).setDuration(180).start();
            return;
        }
        View d = ((ViewGroup) v).getChildAt(0);
        d.animate().scaleX(on ? DISC_SCALE : 1f).scaleY(on ? DISC_SCALE : 1f).setDuration(200).start();
        ((ViewGroup) v).getChildAt(1).animate().alpha(on ? 1f : 0.7f).setDuration(200).start();
    }

    private void styleExit(boolean on) {
        int fg = on ? Color.BLACK : 0xCCFFFFFF;
        exit.setTextColor(fg);
        exit.setCompoundDrawableTintList(ColorStateList.valueOf(fg));
        exit.setBackground(on ? a.round(0xF2FFFFFF, a.px(20), 0) : a.round(0x1AFFFFFF, a.px(20), 0x26FFFFFF));
    }

    private void open(Series s) {
        Uri uri = Uri.parse("https://www.youtube.com/channel/" + s.channel);
        Intent i = new Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube.tv");
        if (!a.launch(i)) a.launch(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void glowTo(int c) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, c);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(v -> paintGlow((int) v.getAnimatedValue()));
        glowAnim.start();
    }

    private void paintGlow(int c) {
        glowColor = c;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(a.px(480));
        int rgb = c & 0x00FFFFFF;
        g.setColors(new int[]{0x4D000000 | rgb, 0x1F000000 | rgb, 0x08000000 | rgb, rgb});
        glow.setBackground(g);
    }

    private RectF rectInContent(View v) {
        float x = 0, y = 0;
        View c = v;
        while (c != content && c != null) {
            x += c.getLeft() - (c == v ? 0 : c.getScrollX());
            y += c.getTop() - (c == v ? 0 : c.getScrollY());
            c = c.getParent() instanceof View ? (View) c.getParent() : null;
        }
        return new RectF(x, y, x + v.getWidth(), y + v.getHeight());
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout tile = new LinearLayout(a);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setClipChildren(false);
            tile.setLayoutParams(new RecyclerView.LayoutParams(spanW, -2));

            ImageView art = new ImageView(a);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setBackgroundColor(0xFF1C1C1E);
            art.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            art.setClipToOutline(true);
            tile.addView(art, new LinearLayout.LayoutParams(disc, disc));

            TextView label = a.text(19, a.medium, Color.WHITE);
            label.setSingleLine();
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setAlpha(0.7f);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(spanW - a.px(8), -2);
            llp.topMargin = a.px(16);
            llp.bottomMargin = a.px(26);
            tile.addView(label, llp);
            return new RecyclerView.ViewHolder(tile) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            Series s = series.get(pos);
            ViewGroup tile = (ViewGroup) h.itemView;
            ((ImageView) tile.getChildAt(0)).setImageBitmap(s.art);
            ((TextView) tile.getChildAt(1)).setText(s.name);
        }

        @Override
        public int getItemCount() {
            return series.size();
        }
    }
}
