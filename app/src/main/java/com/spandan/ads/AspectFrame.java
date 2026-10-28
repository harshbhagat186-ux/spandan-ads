package com.spandan.ads;

import android.content.Context;
import android.widget.FrameLayout;

/** FrameLayout whose height follows its width by a fixed ratio (e.g. 4:5 posters, 9:16 reels). */
public class AspectFrame extends FrameLayout {
    private final float ratio;

    public AspectFrame(Context ctx, float heightOverWidth) {
        super(ctx);
        this.ratio = heightOverWidth;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.round(w * ratio), MeasureSpec.EXACTLY));
    }
}
