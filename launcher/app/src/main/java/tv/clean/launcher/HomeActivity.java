package tv.clean.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends Activity {
    private static final int TOP = 0, DOCK = 1, GRID = 2;
    private static final float TILE_SCALE = 1.1f, PILL_SCALE = 1.06f;

    // Layout metrics, in pixels.
    private float dp;
    private int screenW, screenH, columns, side, spanW, tileW, tileH, bandH, bandBottom;

    Typeface thin, light, regular, medium;
    private ScrollView scroller;
    private FrameLayout content;
    private GlassLayer glass;
    private FrameLayout header, band;
    private TextView greeting, clock, date, title;
    private View glow;
    private int glowColor = 0xFF8E8E93;
    private ValueAnimator glowAnim;
    private final TextView[] pills = new TextView[4];
    private RecyclerView dockRv, gridRv;
    private final List<App> dockApps = new ArrayList<>(), gridApps = new ArrayList<>();
    private final Map<String, Integer> colorCache = new HashMap<>();

    private LinearLayout menu;
    private final List<Runnable> menuActions = new ArrayList<>();
    private int menuIndex;

    private int zone = DOCK, index, lastDock, lastTop;
    private View focusedView;
    private SharedPreferences prefs;

    private KidsScreen kids;
    private PinPad pin;
    private boolean kidsMode;

    static class App {
        String pkg, label;
        Drawable banner, icon;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        dp = getResources().getDisplayMetrics().density;
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        columns = Math.max(4, Math.min(7, getResources().getInteger(R.integer.columns)));
        side = px(56);
        spanW = (screenW - 2 * side) / columns;
        tileW = spanW - px(22);
        tileH = tileW * 9 / 16;
        bandH = tileH + px(32);
        bandBottom = px(22);

        thin = getResources().getFont(R.font.outfit_200);
        light = getResources().getFont(R.font.outfit_300);
        regular = getResources().getFont(R.font.outfit_400);
        medium = getResources().getFont(R.font.outfit_500);

        buildUi();
        loadApps();
        zone = prefs.getInt("zone", DOCK);
        index = prefs.getInt("index", 0);
        lastDock = prefs.getInt("lastDock", 0);
        if (zone == TOP) zone = DOCK;
        content.getViewTreeObserver().addOnGlobalLayoutListener(this::onLayoutSettled);

        IntentFilter pkgs = new IntentFilter();
        pkgs.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgs.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgs.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgs.addDataScheme("package");
        registerReceiver(packageReceiver, pkgs);

        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clockReceiver, time);

        IntentFilter net = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        net.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(netReceiver, net);

        // Kids Mode survives reboots, so restarting the TV is not a way out of it.
        if (prefs.getBoolean("kids", false)) enterKids();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        updateNetwork();
        loadWallpaper();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: close the menu and go back to the dock.
        closeMenu();
        pin.close();
        if (kidsMode) {
            kids.home();
            return;
        }
        if (zone != DOCK) {
            setFocus(DOCK, lastDock, true);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        unregisterReceiver(clockReceiver);
        unregisterReceiver(netReceiver);
        super.onDestroy();
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadApps();
        }
    };
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };
    private final BroadcastReceiver netReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateNetwork();
        }
    };

    // ---------------------------------------------------------------- views

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView wallpaper = new ImageView(this);
        wallpaper.setId(R.id.wallpaper);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setVisibility(View.GONE);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setFocusable(false);
        scroller.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(scroller, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        scroller.addView(content, new ScrollView.LayoutParams(-1, -2));

        glass = new GlassLayer(this);
        content.addView(glass, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        content.addView(column, new FrameLayout.LayoutParams(-1, -2));

        header = buildHeader();
        column.addView(header, new LinearLayout.LayoutParams(-1, screenH - bandH - bandBottom));

        band = new FrameLayout(this);
        band.setClipChildren(false);
        dockRv = buildRecycler(false);
        band.addView(dockRv, new FrameLayout.LayoutParams(-1, -1));
        column.addView(band, new LinearLayout.LayoutParams(-1, bandH));

        column.addView(new View(this), new LinearLayout.LayoutParams(-1, bandBottom + px(40)));

        gridRv = buildRecycler(true);
        column.addView(gridRv, new LinearLayout.LayoutParams(-1, -2));

        // Room for the last grid row to scroll up to the top of the screen.
        column.addView(new View(this), new LinearLayout.LayoutParams(-1, screenH - px(56) - tileH - px(60)));

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(px(8), px(8), px(8), px(8));
        menu.setBackground(round(0xF22A2A2D, px(20), 0x33FFFFFF));
        menu.setElevation(px(12));
        menu.setVisibility(View.GONE);
        root.addView(menu, new FrameLayout.LayoutParams(px(240), -2));

        kids = new KidsScreen(this, this::askToLeaveKids);
        kids.setVisibility(View.GONE);
        root.addView(kids, new FrameLayout.LayoutParams(-1, -1));

        pin = new PinPad(this);
        root.addView(pin, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private FrameLayout buildHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setClipChildren(false);

        // A circle that fades to nothing at its own edge, centred just above the dock title.
        int r = px(440);
        glow = new View(this);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(2 * r, 2 * r);
        glp.leftMargin = side + px(150) - r;
        glp.topMargin = screenH - bandH - bandBottom - px(30) - r;
        h.addView(glow, glp);
        paintGlow(glowColor);

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        greeting = text(17, regular, 0xB3FFFFFF);
        clock = text(68, thin, Color.WHITE);
        clock.setIncludeFontPadding(false);
        clock.setLetterSpacing(-0.02f);
        date = text(17, light, 0x99FFFFFF);
        time.addView(greeting);
        time.addView(clock);
        time.addView(date);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2);
        tlp.leftMargin = side + px(8);
        tlp.topMargin = px(34);
        h.addView(time, tlp);

        LinearLayout bar = new LinearLayout(this);
        bar.setClipChildren(false);
        int[] icons = {R.drawable.ic_input, R.drawable.ic_wifi, R.drawable.ic_settings, R.drawable.ic_kids};
        String[] labels = {"Inputs", "Wi-Fi", "Settings", "Kids"};
        for (int i = 0; i < pills.length; i++) {
            TextView p = text(15, medium, Color.WHITE);
            p.setText(labels[i]);
            p.setSingleLine();
            p.setGravity(Gravity.CENTER_VERTICAL);
            p.setPadding(px(14), 0, px(18), 0);
            p.setCompoundDrawablesWithIntrinsicBounds(icons[i], 0, 0, 0);
            p.setCompoundDrawablePadding(px(8));
            p.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            p.setBackground(round(0x24FFFFFF, px(20), 0x2EFFFFFF));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(40));
            lp.leftMargin = px(12);
            bar.addView(p, lp);
            pills[i] = p;
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2);
        blp.gravity = Gravity.TOP | Gravity.END;
        blp.rightMargin = side;
        blp.topMargin = px(40);
        h.addView(bar, blp);

        title = text(42, light, 0xF2FFFFFF);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams ttl = new FrameLayout.LayoutParams(screenW - 2 * side, -2);
        ttl.gravity = Gravity.BOTTOM;
        ttl.leftMargin = side + px(11);
        ttl.bottomMargin = px(16);
        h.addView(title, ttl);
        return h;
    }

    private RecyclerView buildRecycler(boolean labels) {
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new GridLayoutManager(this, columns) {
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
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setPadding(side, labels ? 0 : px(16), side, 0);
        rv.setAdapter(new TileAdapter(labels ? gridApps : dockApps, labels));
        return rv;
    }

    private class TileAdapter extends RecyclerView.Adapter<TileAdapter.Holder> {
        final List<App> apps;
        final boolean labels;

        TileAdapter(List<App> apps, boolean labels) {
            this.apps = apps;
            this.labels = labels;
        }

        class Holder extends RecyclerView.ViewHolder {
            final FrameLayout card;
            final ImageView image;
            final TextView inner, label;

            Holder(LinearLayout v, FrameLayout card, ImageView image, TextView inner, TextView label) {
                super(v);
                this.card = card;
                this.image = image;
                this.inner = inner;
                this.label = label;
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout tile = new LinearLayout(HomeActivity.this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setClipChildren(false);
            tile.setLayoutParams(new RecyclerView.LayoutParams(spanW, -2));

            FrameLayout card = new FrameLayout(HomeActivity.this);
            card.setBackground(round(0xFF1C1C1E, px(12), 0));
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(12));
                }
            });
            card.setClipToOutline(true);
            card.setPivotX(tileW / 2f);
            card.setPivotY(tileH / 2f);
            ImageView image = new ImageView(HomeActivity.this);
            card.addView(image, new FrameLayout.LayoutParams(-1, -1));
            TextView inner = text(13, regular, 0xCCFFFFFF);
            inner.setSingleLine();
            inner.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            inner.setPadding(px(10), 0, px(10), px(8));
            inner.setGravity(Gravity.CENTER_HORIZONTAL);
            card.addView(inner, ilp);
            tile.addView(card, new LinearLayout.LayoutParams(tileW, tileH));

            TextView label = text(15, regular, Color.WHITE);
            label.setSingleLine();
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setAlpha(0f);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(tileW, labels ? -2 : 0);
            llp.topMargin = labels ? px(12) : 0;
            llp.bottomMargin = labels ? px(14) : 0;
            tile.addView(label, llp);
            return new Holder(tile, card, image, inner, label);
        }

        @Override
        public void onBindViewHolder(Holder h, int pos) {
            App a = apps.get(pos);
            h.label.setText(a.label);
            h.card.setScaleX(1f);
            h.card.setScaleY(1f);
            h.label.setAlpha(0f);
            if (a.banner != null) {
                h.image.setScaleType(ImageView.ScaleType.FIT_XY);
                h.image.setPadding(0, 0, 0, 0);
                h.image.setImageDrawable(a.banner);
                h.inner.setVisibility(View.GONE);
            } else {
                h.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                h.image.setPadding(0, px(14), 0, px(30));
                h.image.setImageDrawable(a.icon);
                h.inner.setText(a.label);
                h.inner.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }

    // ---------------------------------------------------------------- data

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<String> hidden = Arrays.asList(getResources().getStringArray(R.array.hidden_apps));
        Map<String, App> all = new HashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg) || all.containsKey(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm));
            a.banner = ri.activityInfo.loadBanner(pm);
            if (a.banner == null) a.icon = ri.loadIcon(pm);
            all.put(pkg, a);
        }
        dockApps.clear();
        for (String pkg : getResources().getStringArray(R.array.dock_apps)) {
            App a = all.remove(pkg);
            if (a != null && dockApps.size() < columns) dockApps.add(a);
        }
        gridApps.clear();
        gridApps.addAll(all.values());
        Collections.sort(gridApps, (x, y) -> x.label.compareToIgnoreCase(y.label));
        dockRv.getAdapter().notifyDataSetChanged();
        gridRv.getAdapter().notifyDataSetChanged();
        focusedView = null;
        content.post(() -> setFocus(zone, index, false));
    }

    private boolean settled;

    private void onLayoutSettled() {
        View b = band;
        if (b.getHeight() == 0) return;
        float top = header.getHeight();
        glass.setBand(new RectF(side - px(14), top, screenW - side + px(14), top + b.getHeight()));
        if (!settled) {
            settled = true;
            setFocus(zone, index, false);
        }
    }

    // ---------------------------------------------------------------- focus

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (pin.isOpen()) return pin.onKey(e) || super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        boolean dpad = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
        boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE;
        if (!dpad && !ok && !back) return super.dispatchKeyEvent(e);
        if (kidsMode) return kids.onKey(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;

        if (menu.getVisibility() == View.VISIBLE) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) selectMenu(menuIndex - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) selectMenu(menuIndex + 1);
            else if (ok && e.getRepeatCount() == 0) {
                Runnable r = menuActions.get(menuIndex);
                closeMenu();
                r.run();
            } else if (back || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) closeMenu();
            return true;
        }
        if (back) {
            if (zone != DOCK) setFocus(DOCK, lastDock, true);
            return true;
        }
        if (ok) {
            if (e.getRepeatCount() == 0) activate();
            return true;
        }

        int z = zone, i = index;
        switch (zone) {
            case TOP:
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) i = Math.max(0, i - 1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) i = Math.min(pills.length - 1, i + 1);
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN && !dockApps.isEmpty()) {
                    z = DOCK;
                    i = Math.min(lastDock, dockApps.size() - 1);
                } else if (k == KeyEvent.KEYCODE_DPAD_DOWN && !gridApps.isEmpty()) {
                    z = GRID;
                    i = 0;
                }
                break;
            case DOCK:
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) i = Math.max(0, i - 1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) i = Math.min(dockApps.size() - 1, i + 1);
                else if (k == KeyEvent.KEYCODE_DPAD_UP) {
                    z = TOP;
                    i = lastTop;
                } else if (k == KeyEvent.KEYCODE_DPAD_DOWN && !gridApps.isEmpty()) {
                    z = GRID;
                    i = Math.min(index, gridApps.size() - 1);
                }
                break;
            case GRID:
                int row = i / columns, col = i % columns;
                if (k == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) i--;
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && col < columns - 1 && i + 1 < gridApps.size()) i++;
                else if (k == KeyEvent.KEYCODE_DPAD_UP) {
                    if (row == 0) {
                        if (!dockApps.isEmpty()) {
                            z = DOCK;
                            i = Math.min(col, dockApps.size() - 1);
                        } else {
                            z = TOP;
                            i = lastTop;
                        }
                    } else i -= columns;
                } else if (k == KeyEvent.KEYCODE_DPAD_DOWN && (row + 1) * columns < gridApps.size()) {
                    i = Math.min(i + columns, gridApps.size() - 1);
                }
                break;
        }
        if (z != zone || i != index) setFocus(z, i, true);
        return true;
    }

    private void setFocus(int z, int i, boolean animate) {
        if (z == DOCK && dockApps.isEmpty()) z = gridApps.isEmpty() ? TOP : GRID;
        if (z == GRID && gridApps.isEmpty()) z = dockApps.isEmpty() ? TOP : DOCK;
        int size = z == TOP ? pills.length : z == DOCK ? dockApps.size() : gridApps.size();
        i = Math.max(0, Math.min(i, size - 1));

        View target = viewFor(z, i);
        if (target == null) return;
        if (focusedView != null && focusedView != target) styleFocus(focusedView, zone, false);
        zone = z;
        index = i;
        if (z == DOCK) lastDock = i;
        if (z == TOP) lastTop = i;
        focusedView = target;
        styleFocus(target, z, true);

        float s = z == TOP ? PILL_SCALE : TILE_SCALE;
        View box = z == TOP ? target : ((ViewGroup) target).getChildAt(0);
        RectF r = rectInContent(box);
        float dx = r.width() * (s - 1) / 2, dy = r.height() * (s - 1) / 2;
        float out = px(z == TOP ? 4 : 6);
        r.inset(-dx - out, -dy - out);
        glass.moveTo(r, z == TOP ? r.height() / 2 : px(17), animate);

        int scrollY = 0;
        if (z == GRID) scrollY = gridRv.getTop() + target.getTop() - px(56);
        if (animate) scroller.smoothScrollTo(0, scrollY);
        else scroller.scrollTo(0, scrollY);

        if (z == DOCK) showTitle(dockApps.get(i));
        title.animate().alpha(z == GRID ? 0f : 1f).setDuration(200).start();

        prefs.edit().putInt("zone", z).putInt("index", i).putInt("lastDock", lastDock).apply();
    }

    private View viewFor(int z, int i) {
        if (z == TOP) return pills[i];
        RecyclerView rv = z == DOCK ? dockRv : gridRv;
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        return lm == null ? null : lm.findViewByPosition(i);
    }

    private void styleFocus(View v, int z, boolean on) {
        if (z == TOP) {
            TextView p = (TextView) v;
            int fg = on ? Color.BLACK : Color.WHITE;
            p.setTextColor(fg);
            p.setCompoundDrawableTintList(ColorStateList.valueOf(fg));
            p.setBackground(on ? round(0xF2FFFFFF, px(20), 0) : round(0x24FFFFFF, px(20), 0x2EFFFFFF));
            p.animate().scaleX(on ? PILL_SCALE : 1f).scaleY(on ? PILL_SCALE : 1f).setDuration(180).start();
            return;
        }
        ViewGroup tile = (ViewGroup) v;
        View card = tile.getChildAt(0), label = tile.getChildAt(1);
        tile.setElevation(on ? 1 : 0);
        card.animate().scaleX(on ? TILE_SCALE : 1f).scaleY(on ? TILE_SCALE : 1f).setDuration(200).start();
        if (z == GRID) label.animate().alpha(on ? 1f : 0f).setDuration(200).start();
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

    private void activate() {
        if (zone == TOP) {
            if (index == 0) openInputs();
            else if (index == 1) launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
            else if (index == 2) launch(new Intent(Settings.ACTION_SETTINGS));
            else startKids();
            return;
        }
        App a = (zone == DOCK ? dockApps : gridApps).get(index);
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(a.pkg);
        if (i != null) launch(i);
    }

    // ---------------------------------------------------------------- kids mode

    /** Entering needs no PIN; the first time, a grown-up chooses one. */
    private void startKids() {
        if (prefs.getString("pin", null) != null) {
            enterKids();
            return;
        }
        pin.open("Choose a 4-digit parent PIN", first ->
                pin.open("Enter the same PIN again", second -> {
                    if (!second.equals(first)) {
                        pin.close();
                        startKids();
                        pin.fail("The PINs didn't match. Choose one again.");
                        return;
                    }
                    prefs.edit().putString("pin", first).apply();
                    pin.close();
                    enterKids();
                }));
    }

    private void enterKids() {
        kidsMode = true;
        prefs.edit().putBoolean("kids", true).apply();
        closeMenu();
        scroller.setVisibility(View.GONE);
        kids.setVisibility(View.VISIBLE);
        kids.home();
    }

    private void askToLeaveKids() {
        pin.open("Parent PIN", p -> {
            if (!p.equals(prefs.getString("pin", null))) {
                pin.fail("That's not the PIN. Try again.");
                return;
            }
            pin.close();
            kidsMode = false;
            prefs.edit().putBoolean("kids", false).apply();
            kids.setVisibility(View.GONE);
            scroller.setVisibility(View.VISIBLE);
            setFocus(DOCK, lastDock, false);
        });
    }

    boolean launch(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- dock title and glow

    private void showTitle(App a) {
        title.setText(a.label);
        Integer c = colorCache.get(a.pkg);
        if (c == null) {
            c = sampleColor(a.banner != null ? a.banner : a.icon);
            colorCache.put(a.pkg, c);
        }
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
        g.setGradientRadius(px(440));
        int rgb = c & 0x00FFFFFF;
        g.setColors(new int[]{0x61000000 | rgb, 0x29000000 | rgb, 0x0A000000 | rgb, rgb});
        glow.setBackground(g);
    }

    /** The most vivid hue in the drawable, brightened so it glows on black. */
    static int sampleColor(Drawable d) {
        if (d == null || d.getConstantState() == null) return 0xFF8E8E93;
        Drawable copy = d.getConstantState().newDrawable().mutate();
        Bitmap b = Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888);
        copy.setBounds(0, 0, 48, 27);
        copy.draw(new Canvas(b));
        float[] weight = new float[12], hueX = new float[12], hueY = new float[12];
        float[] hsv = new float[3];
        float total = 0;
        for (int y = 0; y < 27; y++) {
            for (int x = 0; x < 48; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 200) continue;
                Color.colorToHSV(p, hsv);
                float w = hsv[1] * hsv[1] * hsv[2];
                if (w < 0.05f) continue;
                int bucket = (int) (hsv[0] / 30f) % 12;
                weight[bucket] += w;
                hueX[bucket] += Math.cos(Math.toRadians(hsv[0])) * w;
                hueY[bucket] += Math.sin(Math.toRadians(hsv[0])) * w;
                total += w;
            }
        }
        b.recycle();
        if (total < 4) return 0xFF8E8E93;
        int best = 0;
        for (int i = 1; i < 12; i++) if (weight[i] > weight[best]) best = i;
        float hue = (float) Math.toDegrees(Math.atan2(hueY[best], hueX[best]));
        if (hue < 0) hue += 360;
        return Color.HSVToColor(new float[]{hue, 0.85f, 1f});
    }

    // ---------------------------------------------------------------- top bar

    private void updateClock() {
        Date now = new Date();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 5 && h < 12 ? "Good morning" : h < 17 && h >= 12 ? "Good afternoon"
                : h >= 17 && h < 22 ? "Good evening" : "Good night");
        boolean is24 = DateFormat.is24HourFormat(this);
        clock.setText(DateFormat.format(is24 ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEEE, d MMMM", now));
    }

    @SuppressWarnings("deprecation")
    private void updateNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        String label;
        if (ni == null || !ni.isConnected()) label = "Offline";
        else if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) label = "Ethernet";
        else if (ni.getType() == ConnectivityManager.TYPE_WIFI) label = ssid();
        else label = "Connected";
        if (label.length() > 20) label = label.substring(0, 19) + "…";
        pills[1].setText(label);
        if (zone == TOP && settled) content.post(() -> setFocus(zone, index, false));
    }

    @SuppressWarnings("deprecation")
    private String ssid() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return "Wi-Fi";
        WifiInfo info = wm.getConnectionInfo();
        String s = info == null ? null : info.getSSID();
        if (s == null || s.contains("unknown ssid")) {
            s = null;
            try {
                List<WifiConfiguration> saved = wm.getConfiguredNetworks();
                if (saved != null && info != null)
                    for (WifiConfiguration c : saved) if (c.networkId == info.getNetworkId()) s = c.SSID;
            } catch (SecurityException ignored) {
            }
        }
        if (s == null || s.isEmpty()) return "Wi-Fi";
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) s = s.substring(1, s.length() - 1);
        return s;
    }

    private void openInputs() {
        menu.removeAllViews();
        menuActions.clear();
        List<String[]> rows = new ArrayList<>(); // label, uri, sort key
        TvInputManager tim = getSystemService(TvInputManager.class);
        String tuner = null;
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isHidden(this)) continue;
                if (!info.isPassthroughInput()) {
                    if (tuner == null) tuner = info.getId();
                    continue;
                }
                CharSequence custom = info.loadCustomLabel(this);
                String label = custom != null ? custom.toString() : String.valueOf(info.loadLabel(this));
                int type = info.getType();
                String key = type == TvInputInfo.TYPE_HDMI ? "0" : "1";
                if (type == TvInputInfo.TYPE_COMPOSITE && !label.toUpperCase(Locale.ROOT).contains("AV")) label = "AV";
                rows.add(new String[]{label, TvContract.buildChannelUriForPassthroughInput(info.getId()).toString(), key + label});
            }
        }
        Collections.sort(rows, (x, y) -> x[2].compareToIgnoreCase(y[2]));
        for (String[] r : rows) addMenuItem(r[0], () -> watch(Uri.parse(r[1]), "vnd.android.cursor.item/channel"));
        final String tunerId = tuner;
        addMenuItem("Live TV", () -> {
            if (tunerId == null || !watch(TvContract.buildChannelsUriForInput(tunerId), "vnd.android.cursor.dir/channel")) {
                // Fall back to whichever app plays channels (the maker's TV app), opened normally.
                Intent view = new Intent(Intent.ACTION_VIEW).setType("vnd.android.cursor.dir/channel");
                for (ResolveInfo ri : getPackageManager().queryIntentActivities(view, 0)) {
                    Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(ri.activityInfo.packageName);
                    if (i != null && launch(i)) break;
                }
            }
        });

        int[] loc = new int[2];
        pills[0].getLocationInWindow(loc);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menu.getLayoutParams();
        lp.leftMargin = Math.min(loc[0], screenW - side - lp.width);
        lp.topMargin = loc[1] + pills[0].getHeight() + px(12);
        menu.setLayoutParams(lp);
        menu.setAlpha(0f);
        menu.setTranslationY(-px(8));
        menu.setVisibility(View.VISIBLE);
        menu.animate().alpha(1f).translationY(0).setDuration(160).start();
        selectMenu(0);
    }

    private void addMenuItem(String label, Runnable action) {
        TextView t = text(17, regular, Color.WHITE);
        t.setText(label);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(16), 0, px(16), 0);
        menu.addView(t, new LinearLayout.LayoutParams(-1, px(44)));
        menuActions.add(action);
    }

    private void selectMenu(int i) {
        if (menuActions.isEmpty()) return;
        menuIndex = Math.max(0, Math.min(i, menuActions.size() - 1));
        for (int n = 0; n < menu.getChildCount(); n++) {
            menu.getChildAt(n).setBackground(n == menuIndex ? round(0x38FFFFFF, px(12), 0) : null);
        }
    }

    private void closeMenu() {
        if (menu.getVisibility() == View.VISIBLE) menu.setVisibility(View.GONE);
    }

    private boolean watch(Uri uri, String type) {
        if (launch(new Intent(Intent.ACTION_VIEW, uri))) return true;
        return launch(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type));
    }

    // ---------------------------------------------------------------- wallpaper

    private void loadWallpaper() {
        ImageView w = findViewById(R.id.wallpaper);
        File f = new File(getString(R.string.wallpaper_path));
        if (!f.canRead()) {
            w.setVisibility(View.GONE);
            w.setImageDrawable(null);
            return;
        }
        if (f.lastModified() == prefs.getLong("wallpaperStamp", -1) && w.getDrawable() != null) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= screenW) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), o);
        if (bmp == null) return;
        w.setImageBitmap(bmp);
        w.setColorFilter(0x8C000000, android.graphics.PorterDuff.Mode.SRC_ATOP);
        w.setVisibility(View.VISIBLE);
        prefs.edit().putLong("wallpaperStamp", f.lastModified()).apply();
    }

    // ---------------------------------------------------------------- helpers

    int px(float v) {
        return Math.round(v * dp);
    }

    TextView text(float sp, Typeface face, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setTextColor(color);
        return t;
    }

    GradientDrawable round(int fill, float radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (strokeColor != 0) g.setStroke(Math.max(1, px(1)), strokeColor);
        return g;
    }
}
