package io.orcana;

import android.graphics.Color;
import android.view.View;

import java.util.ArrayList;

import timber.log.Timber;

public class ButtonManager implements IButtonManager {
    final ArrayList<View> children = new ArrayList<>();
    final ArrayList<View.OnClickListener> onClickList = new ArrayList<>();

    public void addChild(View child, View.OnClickListener onClick) {
        this.children.add(child);
        if (onClick == null) {
            child.setBackgroundColor(Color.GRAY);
        }
        this.onClickList.add(onClick);
    }

    @Override
    public void clickButton(int buttonID) {
        View.OnClickListener listener = this.onClickList.get(buttonID);
        if (listener != null) {
            View b = this.children.get(buttonID);
            Timber.d("clickButton: %s", b.toString());
            listener.onClick(b);
        }
    }

    @Override
    public int onButton(float x, float y) {
        for(int i = 0; i < this.children.size(); ++i){
            View child = this.children.get(i);
            if (x < ((float) child.getLeft()) || x > ((float) child.getRight()) ||
                    y < ((float) child.getTop()) || y > ((float) child.getBottom())) {
                continue;
            }

            View v = this.children.get(i);
            if(v == null || !v.isEnabled() ||
                    v.getVisibility() == View.INVISIBLE || v.getVisibility() == View.GONE){
                continue;
            }

            if (this.onClickList.get(i) == null) {
                return -1;
            }

            return i;
        }

        return -1;
    }
}
