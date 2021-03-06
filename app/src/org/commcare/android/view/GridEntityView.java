/**
 * 
 */
package org.commcare.android.view;

import org.commcare.android.models.Entity;
import org.commcare.android.util.CachingAsyncImageLoader;
import org.commcare.android.util.MarkupUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.util.GridCoordinate;
import org.commcare.util.GridStyle;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathUnhandledException;
import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.AudioController;
import org.odk.collect.android.views.media.ViewId;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.text.Spannable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;

/**
 * @author wspride
 * This class defines an individual panel within an advanced case list.
 * Each panel is defined by a Detail and an Entity
 * Significant axis of configuration are NUMBER_ROWS, NUMBER_COLUMNS, AND CELL_HEIGHT_DIVISOR defined below
 *
 */
@SuppressLint("NewApi")
public class GridEntityView extends GridLayout {

	private String[] forms;
	private String[] searchTerms;
	private GridCoordinate[] coords;
	private GridStyle[] styles;
	Object[] mRowData;
	
	public final float SMALL_FONT = getResources().getDimension(R.dimen.font_size_small);		// load the screen-size dependent font sizes
	public final float MEDIUM_FONT = getResources().getDimension(R.dimen.font_size_medium);	
	public final float LARGE_FONT = getResources().getDimension(R.dimen.font_size_large);
	public final float XLARGE_FONT = getResources().getDimension(R.dimen.font_size_xlarge);
	public final float DENSITY = getResources().getDisplayMetrics().density;
	
	public final int CELL_PADDING_HORIZONTAL = (int)getResources().getDimension(R.dimen.cell_padding_horizontal);
	public final int CELL_PADDING_VERTICAL = (int)getResources().getDimension(R.dimen.cell_padding_vertical);
	public final int ROW_PADDING_HORIZONTAL = (int)getResources().getDimension(R.dimen.row_padding_horizontal);
	public final int ROW_PADDING_VERTICAL = (int)getResources().getDimension(R.dimen.row_padding_vertical);
	
	public final int DEFAULT_NUMBER_ROWS_PER_GRID = 6;
	public final double LANDSCAPE_TO_PORTRAIT_RATIO = .75;
	
	public int NUMBER_ROWS_PER_GRID = 6;															// number of rows per GridView
	public int NUMBER_COLUMNS_PER_GRID = 12;														// number of columns per GridView
	public double NUMBER_ROWS_PER_SCREEN_TALL = 5;													// number of rows the screen is divided into in portrait mode
	public double NUMBER_CROWS_PER_SCREEN_WIDE = 3;	                                                // number of rows the screen is divided into in landscape mode
	
	public double densityRowMultiplier = 1;
	
	public String backgroundColor;
	
	public double cellWidth;
	public double cellHeight;
	
	public double screenWidth;
	public double screenHeight;
	public double rowHeight;
	public double rowWidth;
	private CachingAsyncImageLoader mImageLoader;															// image loader used for all asyncronous imageView loading
	private AudioController controller;
	
	public GridEntityView(Context context, Detail detail, Entity entity, String[] searchTerms, CachingAsyncImageLoader mLoader, AudioController controller) {
		super(context);
		this.searchTerms = searchTerms;
		this.controller = controller;
		
		int maximumRows = this.getMaxRows(detail);
		this.NUMBER_ROWS_PER_GRID = maximumRows;
		// calibrate the size of each gridview relative to the screen size based on how many rows will be in each grid
		// 
		this.NUMBER_ROWS_PER_SCREEN_TALL = this.NUMBER_ROWS_PER_SCREEN_TALL * (this.NUMBER_ROWS_PER_GRID/DEFAULT_NUMBER_ROWS_PER_GRID);
		this.NUMBER_CROWS_PER_SCREEN_WIDE = this.NUMBER_ROWS_PER_SCREEN_TALL * LANDSCAPE_TO_PORTRAIT_RATIO;
		    
		this.setColumnCount(NUMBER_COLUMNS_PER_GRID);
		this.setRowCount(NUMBER_ROWS_PER_GRID);
		this.setPadding(ROW_PADDING_HORIZONTAL,ROW_PADDING_VERTICAL,ROW_PADDING_HORIZONTAL,ROW_PADDING_VERTICAL);
		
		// get density metrics
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		
		int densityDpi = metrics.densityDpi;
		
		if(densityDpi == DisplayMetrics.DENSITY_XHIGH){
			densityRowMultiplier = 2.0;
		} else if(densityDpi == DisplayMetrics.DENSITY_HIGH){
			densityRowMultiplier = 1.5;
		} else if(densityDpi == DisplayMetrics.DENSITY_MEDIUM){
		    
		} 
		
		//setup all the various dimensions we need
		Point size = new Point();
		((Activity)context).getWindowManager().getDefaultDisplay().getSize(size);
		
		screenWidth = size.x;
		screenHeight = size.y;
		
		// If screen is rotated, use width for cell height measurement
		if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
		    //TODO: call to inAwesomeMode was not working for me. What's the best method to determine this?
			if(context.getString(R.string.panes).equals("two")){
			    // if in awesome mode, split available width in half
				screenWidth = screenWidth/2;
			}
			
			// calibrate row width and height based on screen density and divisor constant
			rowHeight = screenHeight/(NUMBER_CROWS_PER_SCREEN_WIDE*densityRowMultiplier);
			rowWidth = screenWidth;
			
		} else{
			rowHeight = screenHeight/(NUMBER_ROWS_PER_SCREEN_TALL*densityRowMultiplier);
			rowWidth = screenWidth;
		}
		
		mImageLoader = mLoader;
		cellWidth = rowWidth/NUMBER_COLUMNS_PER_GRID;
		cellHeight = rowHeight / NUMBER_ROWS_PER_GRID;
		
		// now ready to setup all these views
		setViews(context, detail, entity);

	}
	/**
	 * Add Spaces to this GridLayout to strictly enforce that Grid columns and rows stay the width/height we want
	 * Android GridLayout tries to be very "smart" about moving entries placed arbitrarily within the grid so that
	 * they look "ordered" even though this often ends up looking not how we want it. It will only make these adjustments
	 * to rows/columns that end up empty or near empty, so we solve this by adding spaces to every row and column.
	 * We just add a space of width cellWidth and height 1 to every column of the first row, and likewise a sapce of height 
	 * cellHeight and width 1 to every row of the first column. These are then written on top of if need be.
	 * @param context
	 */
	public void addBuffers(Context context){
		
		for(int i=0; i<NUMBER_ROWS_PER_GRID;i++){
			
			Spec rowSpec = GridLayout.spec(i);
			Spec colSpec = GridLayout.spec(0);
			
			GridLayout.LayoutParams mGridParams = new GridLayout.LayoutParams(rowSpec, colSpec);
			mGridParams.width = 1;
			mGridParams.height = (int)cellHeight;
			
			Space mSpace = new Space(context);
			mSpace.setLayoutParams(mGridParams);
			this.addView(mSpace, mGridParams);
		}
		
		for(int i=0; i<NUMBER_COLUMNS_PER_GRID;i++){
			
			Spec rowSpec = GridLayout.spec(0);
			Spec colSpec = GridLayout.spec(i);
			
			GridLayout.LayoutParams mGridParams = new GridLayout.LayoutParams(rowSpec, colSpec);
			mGridParams.width = (int)cellWidth;
			mGridParams.height = 1;
			
			Space mSpace = new Space(context);
			mSpace.setLayoutParams(mGridParams);
			this.addView(mSpace, mGridParams);
		}
		
	}
	
	// get the maximum height of this grid
	
	public int getMaxRows(Detail detail){
	    
	    GridCoordinate[] coordinates = detail.getGridCoordinates();
	    int currentMaxHeight = 0;
	    
	    for(int i=0; i< coordinates.length; i++){
	        int yCoordinate = coordinates[i].getY();
	        int height = coordinates[i].getHeight();
	        int maxHeight = yCoordinate + height;
	        if(maxHeight > currentMaxHeight){
	            currentMaxHeight = maxHeight;
	        }
	    }

	    return currentMaxHeight;
	}

	/**
	 * Set all the views to be displayed in this pane
	 * @param context
	 * @param detail - the Detail describing how to display each entry
	 * @param entity - the Entity describing the actual data of each entry
	 */
	public void setViews(Context context, Detail detail, Entity entity){
		
		// clear all previous entries in this grid
		this.removeAllViews();
		
		// add spacers to enforce regularized column and row size
		addBuffers(context);
		
		// extract UI information from detail and entity
		forms = detail.getTemplateForms();
		coords = detail.getGridCoordinates();
		styles = detail.getGridStyles();
		mRowData = entity.getData();
		
		String[] bgData = entity.getBackgroundData();
		
		this.setBackgroundDrawable(null);
		
		// see if any entities have background data set
		for(int i=0; i<bgData.length; i++){
			if(!bgData[i].equals("")){
				if(bgData[i].equals(("red-border"))){
					this.setBackgroundDrawable(getResources().getDrawable(R.drawable.border_red));
				} else if(bgData[i].equals(("yellow-border"))){
					this.setBackgroundDrawable(getResources().getDrawable(R.drawable.border_yellow));
				} else if(bgData[i].equals(("red-background"))){
                    this.setBackgroundDrawable(getResources().getDrawable(R.drawable.background_red));
                } else if(bgData[i].equals(("yellow-background"))){
                    this.setBackgroundDrawable(getResources().getDrawable(R.drawable.background_yellow));
                }
			}
		}
		
		this.setPadding(ROW_PADDING_HORIZONTAL,ROW_PADDING_VERTICAL,ROW_PADDING_HORIZONTAL,ROW_PADDING_VERTICAL);

		// iterate through every entity to be inserted in this view
		for(int i=0; i<mRowData.length; i++){
			
			String multimediaType = forms[i];
			GridStyle mStyle = styles[i];
			GridCoordinate currentCoordinate = coords[i];

			// if X and Y coordinates haven't been set, skip this row
			// if span exceeds allotted dimensions, skip this row and log 
			if(currentCoordinate.getX()<0 || currentCoordinate.getY() <0){
			
				if(currentCoordinate.getX() + currentCoordinate.getWidth() > NUMBER_COLUMNS_PER_GRID || 
						currentCoordinate.getY() + currentCoordinate.getHeight() > NUMBER_ROWS_PER_GRID){
					Logger.log("e", "Grid entry dimensions exceed allotted sizes");
					throw new XPathUnhandledException("grid coordinates: " + currentCoordinate.getX() + currentCoordinate.getWidth() + ", " + 
					        currentCoordinate.getY() + currentCoordinate.getHeight() + " out of bounds");
				}
				continue;
			}
			
			View mView;
			
			// these Specs set our span across rows and columns; first arg is root, second is span
			Spec columnSpec = GridLayout.spec(currentCoordinate.getX(), currentCoordinate.getWidth());
			Spec rowSpec = GridLayout.spec(currentCoordinate.getY(), currentCoordinate.getHeight());
			
			ViewId uniqueId = new ViewId(currentCoordinate.getX(), currentCoordinate.getY(), false);

			// setup our layout parameters
			GridLayout.LayoutParams mGridParams = new GridLayout.LayoutParams(rowSpec, columnSpec);
			mGridParams.width = (int)cellWidth * currentCoordinate.getWidth();
			mGridParams.height = (int)cellHeight * currentCoordinate.getHeight();
			
			// get style attributes
			String horzAlign = mStyle.getHorzAlign();
			String vertAlign = mStyle.getVertAlign();
			String textsize = mStyle.getFontSize();
			String CssID = mStyle.getCssID();
			
			mView = getView(context, multimediaType, mGridParams, horzAlign, vertAlign, textsize, entity.getFieldString(i), uniqueId, CssID);

			mView.setLayoutParams(mGridParams);
		
			this.addView(mView, mGridParams);
		}
        
	}
	
	/**
	 * Get the correct View for this particular activity.
	 * @param context
	 * @param multimediaType either "image", "audio", or default text. Describes how this XPath result should be displayed.
	 * @param width the width, in number of cells, this entity should occupy in the grid
	 * @param height the height, in number of cells, this entity should occupy in the grid
	 * @param horzAlign How the text should be aligned horizontally - left, center, or right ONE OF horzAlign or vertAlign
	 * @param vertAlign How the text should be aligned vertically - top, center, or bottom ONE OF horzAlign or vertAlign
	 * @param textsize The font size, scaled for screen size. small, medium, large, xlarge accepted.
	 * @param rowData The actual data to display, either an XPath to media or a String to display
	 * @return
	 */
	private View getView(Context context, String multimediaType, GridLayout.LayoutParams mGridParams,  String horzAlign, String vertAlign, String textsize, String rowData, ViewId uniqueId, String cssid) {
		View retVal;
		if(multimediaType.equals(EntityView.FORM_IMAGE)){
			retVal = new ImageView(context);
			retVal.setPadding(CELL_PADDING_HORIZONTAL,CELL_PADDING_VERTICAL,CELL_PADDING_HORIZONTAL,CELL_PADDING_VERTICAL);
			// image loading is handled asyncronously by the TCImageLoader class to allow smooth scrolling
			if(rowData != null && !rowData.equals("")){
				mImageLoader.display(rowData, ((ImageView)retVal), R.drawable.info_bubble);
			}
		} 
		else if(multimediaType.equals(EntityView.FORM_AUDIO)){
    		if (rowData != null & rowData.length() > 0) {
    			retVal = new AudioButton(context, rowData, uniqueId, controller, true);
    		}
    		else {
    			retVal = new AudioButton(context, rowData, uniqueId, controller, false);
    		}
		} else{
			retVal = new TextView(context);
			
			if(cssid !=null && !cssid.equals("none")){
			    // user defined a style we want to use
			    Spannable mSpannable = MarkupUtil.getCustomSpannable(cssid, rowData);
			    ((TextView)retVal).setText(mSpannable);
			} else{
			    // just process inline markup 
			    Spannable mSpannable = MarkupUtil.getSpannable(rowData);
			    ((TextView)retVal).setText(mSpannable);
			}

			// handle horizontal alignments
			if(horzAlign.equals("center")){
				((TextView)retVal).setGravity(Gravity.CENTER_HORIZONTAL);
			} else if(horzAlign.equals("left")) {
				((TextView)retVal).setGravity(Gravity.TOP);
			} else if(horzAlign.equals("right")) {
				((TextView)retVal).setGravity(Gravity.RIGHT);
			}  
			// handle vertical alignment
			if(vertAlign.equals("center")){
				((TextView)retVal).setGravity(Gravity.CENTER_VERTICAL);
			} else if(vertAlign.equals("top")) {
				((TextView)retVal).setGravity(Gravity.TOP);
			} else if(vertAlign.equals("bottom")) {
				((TextView)retVal).setGravity(Gravity.BOTTOM);
			}
			
			// handle text resizing
			if(textsize.equals("large")){
				((TextView)retVal).setTextSize(LARGE_FONT/DENSITY);
			} else if(textsize.equals("small")){
				((TextView)retVal).setTextSize(SMALL_FONT/DENSITY);
			} else if(textsize.equals("medium")){
				((TextView)retVal).setTextSize(MEDIUM_FONT/DENSITY);
			} else if(textsize.equals("xlarge")){
				((TextView)retVal).setTextSize(XLARGE_FONT/DENSITY);
			} 
		}
		
		return retVal;
	}
}
