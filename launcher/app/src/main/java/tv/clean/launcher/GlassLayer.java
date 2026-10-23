package tv.clean.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Draws the dock band and the single glass highlight that springs from tile to tile. */
class GlassLayer extends View {
    private final float dp;
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF band = new RectF();
    private final RectF cur = new RectF(), from = new RectF(), to = new RectF();
    private float curRadius, fromRadius, toRadius;
    private boolean placed;
    private ValueAnimator anim;

    GlassLayer(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        bandPaint.setColor(0x17FFFFFF);
        fill.setColor(0x2EFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * dp);
        stroke.setColor(0x73FFFFFF);
    }

    void setBand(RectF r) {
        band.set(r);
        invalidate();
    }

    /** Moves the highlight to {@code r}; jumps there the first time or when {@code animate} is false. */
    void moveTo(RectF r, float radius, boolean animate) {
        if (anim != null) anim.cancel();
        if (!placed || !animate) {
            cur.set(r);
            curRadius = radius;
            placed = true;
            invalidate();
            return;
        }
        from.set(cur);
        fromRadius = curRadius;
        to.set(r);
        toRadius = radius;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(300);
        anim.setInterpolator(new OvershootInterpolator(1.3f));
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            cur.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                    lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t));
            curRadius = Math.max(0, lerp(fromRadius, toRadius, t));
            invalidate();
        });
        anim.start();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!band.isEmpty()) c.drawRoundRect(band, 28 * dp, 28 * dp, bandPaint);
        if (!placed) return;
        c.drawRoundRect(cur, curRadius, curRadius, fill);
        // A soft light from the top edge, so the glass reads as a raised pane rather than a border.
        sheen.setShader(new LinearGradient(0, cur.top, 0, cur.top + cur.height() * 0.6f,
                0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(cur, curRadius, curRadius, sheen);
        c.drawRoundRect(cur, curRadius, curRadius, stroke);
    }
}
