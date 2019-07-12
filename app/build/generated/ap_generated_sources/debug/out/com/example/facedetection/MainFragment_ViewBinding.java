// Generated code from Butter Knife. Do not modify!
package com.example.facedetection;

import android.view.TextureView;
import android.view.View;
import androidx.annotation.CallSuper;
import androidx.annotation.UiThread;
import butterknife.Unbinder;
import butterknife.internal.Utils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.lang.IllegalStateException;
import java.lang.Override;

public class MainFragment_ViewBinding implements Unbinder {
  private MainFragment target;

  @UiThread
  public MainFragment_ViewBinding(MainFragment target, View source) {
    this.target = target;

    target.viewFinder = Utils.findRequiredViewAsType(source, R.id.view_finder, "field 'viewFinder'", TextureView.class);
    target.captureFab = Utils.findRequiredViewAsType(source, R.id.captureFab, "field 'captureFab'", FloatingActionButton.class);
    target.graphicOverlay = Utils.findRequiredViewAsType(source, R.id.rectangleView, "field 'graphicOverlay'", GraphicOverlay.class);
  }

  @Override
  @CallSuper
  public void unbind() {
    MainFragment target = this.target;
    if (target == null) throw new IllegalStateException("Bindings already cleared.");
    this.target = null;

    target.viewFinder = null;
    target.captureFab = null;
    target.graphicOverlay = null;
  }
}
