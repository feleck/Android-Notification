package com.wanikani.androidnotifier.graph;

import java.util.List;
import java.util.Vector;

import com.wanikani.androidnotifier.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.View;

public class PiePlot extends View {

	public static class DataSet {
		
		public String description;
		public int color;
		
		public float value;
		
		Path tpath;

		Path hpath1;
		
		Path hpath2;
		
		Paint fpaint;
		
		Paint spaint;
		
		public DataSet (String description, int color, float value)
		{
			this.description = description;
			this.color = color;
			this.value = value;
		}
	}
	
	private enum Strip {
		
		FRONT {
			public void fillDataSet (DataSet ds, RectF rect, float h, 
									 float angle1, float angle2)
			{
				if (coz (angle1) < coz (angle2)) {
					ds.hpath1 = strip (rect, h, angle1, 180);
					ds.hpath2 = strip (rect, h, 0, angle2);
				} else
					ds.hpath1 = strip (rect, h, angle1, angle2);
			}
		},
		
		BACK {
			
			public void fillDataSet (DataSet ds, RectF rect, float h, 
									 float angle1, float angle2)
			{
				if (coz (angle2) < coz (angle1))
					ds.hpath1 = strip (rect, h, 0, 180);
			}
		},
		
		RIGHT {
			public void fillDataSet (DataSet ds, RectF rect, float h, 
									 float angle1, float angle2)
			{
				if (zin (angle2) < zin (angle1))
					ds.hpath1 = strip (rect, h, 0, angle2);
				else
					ds.hpath1 = strip (rect, h, angle1, 180);
			}			
		},
		
		LEFT {
			public void fillDataSet (DataSet ds, RectF rect, float h, 
									 float angle1, float angle2)
			{
				if (zin (angle1) < zin (angle2))
					ds.hpath1 = strip (rect, h, angle1, 180);
				else
					ds.hpath1 = strip (rect, h, 0, angle2);
			}						
		};
		
		public abstract void fillDataSet (DataSet ds, RectF rect, float h,
										  float angle1, float angle2);		

		private static float coz (float angle)
		{
			return angle < 180 ? 360 - angle : angle;
		}	
		
		private static float zin (float angle)
		{
			if (angle < 90)
				return 180 - angle;
			else if (angle > 270)
				return 540 - angle;
			else
				return angle;
		}
		
		private static Path strip (RectF rect, float h, float angle1, float angle2)
		{
			Path path;
			
			path = new Path ();
			path.arcTo (rect, angle2, angle1 - angle2);
			path.offset (0, h);
			path.arcTo (rect, angle1, angle2 - angle1);
			path.close ();
			
			return path;
		}
	}
	
	private List<DataSet> dsets;
	
	private RectF rect;
	
	private static final int DEFAULT_START_ANGLE = -110;
	
	private static final float DEFAULT_RATIO = 2F;
	
	private static final float DEFAULT_HRATIO = 10;
	
	private int startAngle;
	
	private float ratio;
	
	private float hratio;
		
	/**
	 * Constructor.
	 * @param ctxt context
	 * @param attrs attribute set
	 */
	public PiePlot (Context ctxt, AttributeSet attrs)
	{		
		super (ctxt, attrs);
		
		dsets = new Vector<DataSet> (0);
		
		loadAttributes (ctxt, attrs);
	}
	
	void loadAttributes (Context ctxt, AttributeSet attrs)
	{
		TypedArray a;
		
		a = ctxt.obtainStyledAttributes (attrs, R.styleable.PiePlot);
			 
		startAngle = a.getInteger (R.styleable.PiePlot_start_angle, DEFAULT_START_ANGLE);
		ratio = a.getFloat (R.styleable.PiePlot_ratio, DEFAULT_RATIO);
		hratio = a.getFloat (R.styleable.PiePlot_hratio, DEFAULT_HRATIO);
		
		a.recycle ();
	}
	
	@Override
	public void onMeasure (int widthSpec, int heightSpec)
	{
		int width, height;
		int wMode, hMode;
		
		width = MeasureSpec.getSize (widthSpec);
		height = MeasureSpec.getSize (heightSpec);
		
		wMode = MeasureSpec.getMode (widthSpec);
		hMode = MeasureSpec.getMode (heightSpec);
		
		if (wMode == MeasureSpec.EXACTLY)
			height = measureExact (width, height, hMode, ratio);
		else if (hMode == MeasureSpec.EXACTLY)
			width = measureExact (height, width, wMode, 1F / ratio);
		else if (wMode == MeasureSpec.AT_MOST) {
			height = measureExact (width, height, hMode, ratio);
			width = Math.min (width, height);
		} else if (hMode == MeasureSpec.AT_MOST) {
			width = measureExact (height, width, wMode, 1F / ratio);
			height = Math.min (width, height);
		} else			
			width = height = 100;
		
		setMeasuredDimension (width, height);
	}
	
	protected int measureExact (float a, float b, int bmode, float ratio)
	{
		a /= ratio;
		
		switch (bmode) {
		case MeasureSpec.EXACTLY:
			return Math.round (b);
			
		case MeasureSpec.AT_MOST:
			return Math.round (Math.min (a, b));
			
		case MeasureSpec.UNSPECIFIED:
			return Math.round (a);
			
		default:
			return Math.round (b);
		}
	}
	
	@Override
	protected void onDraw (Canvas canvas)
	{
		for (DataSet ds : dsets) {
			canvas.drawPath (ds.tpath, ds.fpaint);
			if (ds.hpath1 != null)
				canvas.drawPath (ds.hpath1, ds.spaint);
			if (ds.hpath2 != null)
				canvas.drawPath (ds.hpath1, ds.spaint);
		}
		
	}
	
	@Override
	protected void onSizeChanged (int width, int height, int ow, int oh)
	{
		rect = new RectF (0, 0, width, height);
		
		recalc ();
	}
	
	protected void fillTopPath (DataSet ds, RectF rect, float angle, float sweep)
	{
		Path path;
		
		path = new Path ();
		path.moveTo (rect.centerX (), rect.centerY ());
		path.arcTo (rect, angle, sweep);
		path.close ();
		
		ds.tpath = path;
	}
	
	private static boolean isVisible (float angle)
	{
		return angle < 180;
	}
	
	protected void fillHPath (DataSet ds, RectF rect, float h, float angle, float sweep)
	{
		float angle2;
		Strip s;
		
		angle2 = (angle + sweep) % 360;
		if (isVisible (angle))
			if (isVisible (angle2))
				s = Strip.FRONT;
			else
				s = Strip.LEFT;
		else
			if (isVisible (angle2))
				s = Strip.RIGHT;
			else
				s = Strip.BACK;
		
		s.fillDataSet(ds, rect, h, angle, angle2);
	}
	
	protected void recalc ()
	{
		RectF topr;
		float angle, sweep;
		float total;
		float h;
		
		if (dsets.isEmpty () || rect == null)
			return;
		
		h = rect.width () / hratio * (1 - 1F / ratio);
		topr = new RectF (0, 0, rect.width (), rect.height () - h);
		
		total = 0;
		for (DataSet ds : dsets)
			total += ds.value;
		
		angle = startAngle;
		for (DataSet ds : dsets) {
			while (angle < 0)
				angle += 360;
			if (angle > 360)
				angle %= 360;
			
			sweep = ds.value * 360 / total;
			
			fillTopPath (ds, topr, angle, sweep);
			fillHPath (ds, topr, h, angle, sweep);

			ds.fpaint = new Paint ();
			ds.fpaint.setStyle (Style.FILL);
			ds.fpaint.setColor (ds.color);
			ds.fpaint.setAntiAlias (true);
			
			ds.spaint = new Paint ();
			ds.spaint.setStyle (Style.FILL);
			ds.spaint.setColor (shadow (ds.color));
			ds.spaint.setAntiAlias (true);

			angle += sweep;			
		}
	}
	
	protected int shadow (int color)
	{
		float hsv [];

		hsv = new float [3];
		Color.colorToHSV (color, hsv);
		hsv [2] /= 2;
		
		return Color.HSVToColor (hsv);
	}
	
	public void setData (List<DataSet> dsets)
	{
		this.dsets = dsets;
		recalc ();
		invalidate ();
	}
}
