package com.ramotion.expandingcollection;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.os.Build;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import java.util.List;

import ramotion.com.expandingcollection.R;


/**
 * Pager container for simulate needed pager behavior - show parts of nearby pager elements
 */
public class ECPagerView extends FrameLayout implements ViewPager.OnPageChangeListener {
    public static final String TAG = "ecview";

    private ECPager pager;
    private boolean needsRedraw = false;
    private Point center = new Point();
    private Point initialTouch = new Point();
    private ECBackgroundView attachedImageSwitcher;

    private Integer cardWidth;
    private Integer cardHeight;
    private Integer cardHeaderHeightExpanded;

    private int nextTopMargin = 0;


    private OnCardSelectedListener onCardSelectedListener;

    public ECPagerView(Context context) {
        super(context);
        init(context);
    }

    public ECPagerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeFromAttributes(context, attrs);
        init(context);
    }

    public ECPagerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initializeFromAttributes(context, attrs);
        init(context);
    }

    protected void initializeFromAttributes(Context context, AttributeSet attrs) {
        TypedArray array = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ExpandingCollection, 0, 0);
        try {
            this.cardWidth = array.getInt(R.styleable.ExpandingCollection_cardWidth, 400);
            this.cardHeight = array.getColor(R.styleable.ExpandingCollection_cardHeight, 300);
            this.cardHeaderHeightExpanded = array.getInt(R.styleable.ExpandingCollection_cardHeaderHeightExpanded, 300);
        } finally {
            array.recycle();
        }
    }

    private void init(Context context) {
        setClipChildren(false);
        setClipToPadding(false);

        if (Build.VERSION.SDK_INT < 21)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        pager = new ECPager(context);

        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        this.addView(pager, 0, layoutParams);

        pager.setPageTransformer(false, new AlphaScalePageTransformer());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        try {
            pager = (ECPager) getChildAt(0);
            pager.addOnPageChangeListener(this);
            pager.updateLayoutDimensions(cardWidth, cardHeight);
        } catch (Exception e) {
            throw new IllegalStateException("The root child of PagerContainer must be a ViewPager");
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        center.x = w / 2;
        center.y = h / 2;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        //We capture any touches not already handled by the ViewPager
        // to implement scrolling from a touch outside the pager bounds.
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialTouch.x = (int) ev.getX();
                initialTouch.y = (int) ev.getY();
            default:
                ev.offsetLocation(center.x - initialTouch.x, center.y - initialTouch.y);
                break;
        }
        return pager.dispatchTouchEvent(ev);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        //Force the container to redraw on scrolling.
        //Without this the outer pages render initially and then stay static
        if (needsRedraw) invalidate();
    }

    @Override
    public void onPageSelected(int position) {
        int oldPosition = pager.getCurrentPosition();
        pager.setCurrentPosition(position);

        ECBackgroundView.AnimationDirection direction = null;
        int nextPositionPrediction = position;
        if (oldPosition < position) {
            direction = ECBackgroundView.AnimationDirection.LEFT;
            nextPositionPrediction++;
        } else if (oldPosition > position) {
            direction = ECBackgroundView.AnimationDirection.RIGHT;
            nextPositionPrediction--;
        }

        if (attachedImageSwitcher != null) {
            attachedImageSwitcher.setReverseDrawOrder(attachedImageSwitcher.getDisplayedChild() == 1);

            // change current image from cache or reinitialize it from resource
            BackgroundBitmapCache instance = BackgroundBitmapCache.getInstance();
            if (instance.getBitmapFromBgMemCache(position) != null) {
                attachedImageSwitcher.updateCurrentBackground(pager, direction);
            } else {
                attachedImageSwitcher.updateCurrentBackgroundAsync(pager, direction);
            }
            // change background on next predicted position
            attachedImageSwitcher.cacheBackgroundAtPosition(pager, nextPositionPrediction);
        }
        if (onCardSelectedListener != null)
            onCardSelectedListener.cardSelected(oldPosition, position);
    }


    @Override
    public void onPageScrollStateChanged(int state) {
        needsRedraw = (state != ViewPager.SCROLL_STATE_IDLE);
    }

    protected void toggleTopMargin(int duration, int delay) {
        final RelativeLayout.LayoutParams containerLayoutParams = (RelativeLayout.LayoutParams) this.getLayoutParams();

        int currentTopMargin = containerLayoutParams.topMargin;

        ValueAnimator marginAnimation = new ValueAnimator();
        marginAnimation.setInterpolator(new DecelerateInterpolator());
        marginAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                containerLayoutParams.topMargin = (int) animation.getAnimatedValue();
                ECPagerView.this.setLayoutParams(containerLayoutParams);
            }
        });

        marginAnimation.setIntValues(containerLayoutParams.topMargin, nextTopMargin);
        marginAnimation.setDuration(duration);
        marginAnimation.setStartDelay(delay);
        marginAnimation.start();
        nextTopMargin = currentTopMargin;
    }

    public ECPagerView withBackgroundImageSwitcher(ECBackgroundView imageSwitcher) {
        this.attachedImageSwitcher = imageSwitcher;
        if (attachedImageSwitcher == null) return this;
        ECPagerViewAdapter adapter = (ECPagerViewAdapter) this.pager.getAdapter();
        if (adapter != null && adapter.getDataset() != null && adapter.getDataset().size() > 1) {
//            int currentPosition = pager.getCurrentPosition();
//            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), pager.getDataFromAdapterDataset(currentPosition).getMainBgImageDrawableResource());
//            BackgroundBitmapCache.getInstance().addBitmapToBgMemoryCache(currentPosition, bitmap);
//            attachedImageSwitcher.setImageBitmapWithAnimation(bitmap, null);
            attachedImageSwitcher.updateCurrentBackground(pager, null);

        }

        return this;
    }

    public ECPagerView withPagerViewAdapter(ECPagerViewAdapter adapter) {
        this.pager.setAdapter(adapter);
        List<ECCardData> dataset = adapter.getDataset();
        if (dataset != null && dataset.size() > 1 && attachedImageSwitcher != null) {
//            int currentPosition = pager.getCurrentPosition();
//            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), pager.getDataFromAdapterDataset(currentPosition).getMainBgImageDrawableResource());
//            BackgroundBitmapCache.getInstance().addBitmapToBgMemoryCache(currentPosition, bitmap);
            attachedImageSwitcher.updateCurrentBackground(pager, null);
        }
        return this;
    }


    public ECPagerView withCardSize(int cardWidth, int cardHeight) {
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        this.pager.updateLayoutDimensions(cardWidth, cardHeight);
        return this;
    }

    public ECPagerView withCardExpandedHeaderHeight(int cardHeaderHeightExpanded) {
        this.cardHeaderHeightExpanded = cardHeaderHeightExpanded;
        return this;
    }


    public int getCardWidth() {
        return cardWidth;
    }

    public int getCardHeight() {
        return cardHeight;
    }

    public int getCardHeaderHeightExpanded() {
        return cardHeaderHeightExpanded;
    }

    public boolean expand() {
        return pager.expand();
    }

    public boolean collapse() {
        return pager.collapse();
    }

    public boolean toggle() {
        return pager.toggle();
    }

    public interface OnCardSelectedListener {

        void cardSelected(int oldPosition, int newPosition);
    }

}
