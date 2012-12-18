//
// Copyright (C) 2011, 2012 Mocean Mobile. All Rights Reserved. 
//
package com.MASTAdView.core;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.MASTAdView.MASTAdDelegate;
import com.MASTAdView.MASTAdLog;
import com.MASTAdView.MASTAdView;

public class AdWebView extends WebView
{
	final private MASTAdLog adLog;
	final private AdViewContainer adViewContainer;
	private JavascriptInterface javascriptInterface;
	private MraidInterface mraidInterface;
	private String mraidScript;
	private boolean mraidLoaded = false; // has mraid library been loaded?
	private Object mraidLoadSync = new Object();
	final private StringBuffer defferedJavascript;
	private DisplayMetrics metrics;
	final private boolean supportMraid;
	final private boolean launchBrowserOnClicks;
	private AdClickHandler adClickHandler = null;
	
	
	@SuppressLint("SetJavaScriptEnabled")
	public AdWebView(AdViewContainer parent, MASTAdLog log, DisplayMetrics metrics, boolean mraid, boolean handleClicks)
	{
		super(parent.getContext());
		
		adViewContainer = parent;
		adLog = log;
		this.metrics = metrics;
		supportMraid = mraid;
		launchBrowserOnClicks = handleClicks;
		
		//dataToInject = null;
		defferedJavascript = new StringBuffer();
		
		// Customize settings for web view
		WebSettings webSettings = getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setSupportZoom(false);
		webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
		
		// apply standard properties
		setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		
		// Clients for javascript and other integration
		setWebChromeClient(new AdWebChromeClient());
		setWebViewClient(new AdWebViewClient(parent.getContext()));
		
		if (supportMraid)
		{
			javascriptInterface = new JavascriptInterface(parent, this);
			mraidInterface = new MraidInterface(parent, this);
			
			mraidScript = FileUtils.readTextFromJar(parent.getContext(),  "/mraid.js");
		}
		
		//System.out.println("mread script read: " + mraidScript);
		
		if (handleClicks)
		{
			adClickHandler = new AdClickHandler(adViewContainer);
		}
	}

	
	public void setMraidLoaded(boolean value)
	{
		synchronized (mraidLoadSync)
		{
			mraidLoaded = value;
			mraidLoadSync.notify();
		}
	}
	
	
	public boolean getMraidLoaded()
	{
		boolean result = false;
		
		synchronized (mraidLoadSync)
		{
			result = mraidLoaded;
		}
		
		return result;
	}
	
	
	public JavascriptInterface getJavascriptInterface()
	{
		return javascriptInterface;
	}
	
	
	public MraidInterface getMraidInterface()
	{
		return mraidInterface;
	}
	
	
	synchronized public void resetForNewAd()
	{
		stopLoading();
		clearView();
		defferedJavascript.setLength(0);
		//mraidInterface.setState(MraidInterface.STATES.LOADING);
		setMraidLoaded(false);
	}
	
	
	final private class AdWebChromeClient extends WebChromeClient
	{
		@Override
		public boolean onJsAlert(WebView view, String url, String message, JsResult result)
		{
			// Handle alert message from javascript
			System.out.println("JSAlert: " + message);
			return super.onJsAlert(view, url, message, result);
		}
	}
	

	/**
	 * Inject string into webview for execution as javascript.
	 * NOTE: Handle carefully, this has security implications! 
	 * @param str Code string to be run; javascript: prefix will be prepended automatically.
	 */
	synchronized public void injectJavaScript(String str)
	{
		try
		{
			if (supportMraid)
			{
				if (getMraidLoaded())
				{
					adLog.log(MASTAdLog.LOG_LEVEL_DEBUG, "injectJavascript", str);
					loadUrl("javascript:" + str);
				}
				else
				{
					//System.out.println("inject javascript (Deferred): " + str);
					defferedJavascript.append(str);
					defferedJavascript.append("\n");
				}
			}
			else
			{
				adLog.log(MASTAdLog.LOG_LEVEL_DEBUG, "injectJavascript", "disabled, skipping");
			}
		}
		catch (Exception e)
		{
			adLog.log(MASTAdLog.LOG_LEVEL_DEBUG, "injectJavascript - exception", e.getMessage());
		}
	}

	
	private void initializeExpandProperties()
	{
		if (supportMraid)
		{
			List<NameValuePair> list = new ArrayList<NameValuePair>(2);
			
			// Add width
			String name = MraidInterface.get_EXPAND_PROPERTIES_name(MraidInterface.EXPAND_PROPERTIES.WIDTH);
			NameValuePair nvp = new BasicNameValuePair(name, "" + AdSizeUtilities.devicePixelToMraidPoint(metrics.widthPixels, getContext())); 
			list.add(nvp);
			
			// Add height
			name = MraidInterface.get_EXPAND_PROPERTIES_name(MraidInterface.EXPAND_PROPERTIES.HEIGHT);
			nvp = new BasicNameValuePair(name, "" + AdSizeUtilities.devicePixelToMraidPoint(metrics.heightPixels, getContext())); 
			list.add(nvp);
			
			mraidInterface.setExpandProperties(list);
		}
	}
	
	
	private void initializeResizeProperties()
	{
		if (!supportMraid)
		{
			return;
		}
		
		List<NameValuePair> list = new ArrayList<NameValuePair>(2);
		
		// Add width
		String name = MraidInterface.get_RESIZE_PROPERTIES_name(MraidInterface.RESIZE_PROPERTIES.WIDTH);
		NameValuePair nvp = new BasicNameValuePair(name, "" + AdSizeUtilities.devicePixelToMraidPoint(adViewContainer.getWidth(), getContext()));
		list.add(nvp);
		
		// Add height
		name = MraidInterface.get_RESIZE_PROPERTIES_name(MraidInterface.RESIZE_PROPERTIES.HEIGHT);
		nvp = new BasicNameValuePair(name, "" + AdSizeUtilities.devicePixelToMraidPoint(adViewContainer.getHeight(), getContext()));
		list.add(nvp);
		
		AdWebView adWebView = adViewContainer.getAdWebView();
		int[] screenLocation = new int[2];
		adWebView.getLocationOnScreen(screenLocation);
		
		// Add x offset
		name = MraidInterface.get_RESIZE_PROPERTIES_name(MraidInterface.RESIZE_PROPERTIES.OFFSET_X);
		nvp = new BasicNameValuePair(name, "" + AdSizeUtilities.devicePixelToMraidPoint(screenLocation[0], getContext()));
		//nvp = new BasicNameValuePair(name, "" + adViewContainer.getLeft());
		//nvp = new BasicNameValuePair(name, "" + adWebView.getLeft());
		list.add(nvp);
		
		// Add y offset
		name = MraidInterface.get_RESIZE_PROPERTIES_name(MraidInterface.RESIZE_PROPERTIES.OFFSET_Y);
		nvp = new BasicNameValuePair(name, "" + AdSizeUtilities.devicePixelToMraidPoint(screenLocation[1], getContext()));
		//nvp = new BasicNameValuePair(name, "" + adWebView.getTop());
		//nvp = new BasicNameValuePair(name, "" + adViewContainer.getTop());
		list.add(nvp);
				
		mraidInterface.setResizeProperties(list);
	}
	
	
	protected void defaultOnAdClickHandler(AdWebView viev, String url)
	{
		if (adClickHandler != null)
		{
			adClickHandler.openUrlForBrowsing(getContext(), url);
		}
	}
	
	
	private int getStatusBarHeight()
	{
		try
		{
			Rect rect = new Rect(); 
	        Window window = ((Activity)(adViewContainer.getContext())).getWindow(); 
	        window.getDecorView().getWindowVisibleDisplayFrame(rect); 
	        int statusBarHeight = rect.top; 	
	        return statusBarHeight;
		}
		catch(Exception ex)
		{
			// NA
		}
		
		return 0;
	}
	
	
	final private class AdWebViewClient extends WebViewClient
	{
		private Context context;
		
		
		public AdWebViewClient(Context context)
		{
			super();
			this.context = context;
		}
		
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url)
		{
			try
			{
				adLog.log(MASTAdLog.LOG_LEVEL_DEBUG, "OverrideUrlLoading",url);
				MASTAdDelegate delegate = adViewContainer.getAdDelegate();
				if (delegate != null)
				{
					MASTAdDelegate.AdActivityEventHandler clickHandler = delegate.getAdActivityEventHandler(); 
					if ( clickHandler != null)
					{
						if (clickHandler.onAdClicked((MASTAdView)adViewContainer, url) == false)
						{
							// If click() method returns false, continue with default logic
							defaultOnAdClickHandler((AdWebView)view, url);
						}
					}
					else
					{
						defaultOnAdClickHandler((AdWebView)view, url);
					}
				}
				else
				{
					defaultOnAdClickHandler((AdWebView)view, url);
				}
			}
			catch(Exception e)
			{
				adLog.log(MASTAdLog.LOG_LEVEL_ERROR, "shouldOverrideUrlLoading", e.getMessage());
			}
			
			return true;
		}
		
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon)
		{
			adLog.log(MASTAdLog.LOG_LEVEL_DEBUG, "onPageStarted", "loading javascript library");
			
			if (supportMraid)
			{
				loadUrl("javascript:" + mraidScript);
				
				// Wait for mraid loaded to be true, set by js bridge
				if (!getMraidLoaded())
				{
					synchronized (mraidLoadSync)
					{
						try { mraidLoadSync.wait(); } catch (Exception e) { }
					}
				}
				
				mraidInterface.setDeviceFeatures();
				if (mraidInterface.getDeviceFeatures().isSupported(MraidInterface.FEATURES.INLINE_VIDEO))
				{
					if (context instanceof Activity)
					{
						
						
						
						// XXX do this with reflection??? currently causes a problem if enabled
						final int hardwareAccelerated = 16777216; // WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED from API level 11 
						((Activity)context).getWindow().setFlags(hardwareAccelerated, hardwareAccelerated);
						
						
						
					}
					else
					{
						adLog.log(MASTAdLog.LOG_LEVEL_DEBUG, "onPageStarted", "Video support enabled, but context is not an activity, so cannot adjust web view window properties for hardware acceleration");
					}
				} 
				
				if (defferedJavascript.length() > 0)
				{
					// Now that mraid script is loaded, send any commands that were saved from earlier
					injectJavaScript(defferedJavascript.toString());
					defferedJavascript.setLength(0);
				}
			
				// Initialize width/height values for expand properties (starts off with screen size)
				initializeExpandProperties();
			
				// setScreenSize 
				try
				{
					JSONObject screenSize = new JSONObject();
					screenSize.put(MraidInterface.get_SCREEN_SIZE_name(MraidInterface.SCREEN_SIZE.WIDTH), "" + AdSizeUtilities.devicePixelToMraidPoint(metrics.widthPixels, getContext()));
					screenSize.put(MraidInterface.get_SCREEN_SIZE_name(MraidInterface.SCREEN_SIZE.HEIGHT), "" + AdSizeUtilities.devicePixelToMraidPoint(metrics.heightPixels, getContext()));
					injectJavaScript("mraid.setScreenSize(" + screenSize.toString() + ");");
				}
				catch(Exception ex)
				{
					adLog.log(MASTAdLog.LOG_LEVEL_ERROR, "onPageStarted", "Error setting screen size information.");
				}
				
				// setMaxSize
				try
				{
					JSONObject maxSize = new JSONObject();
					maxSize.put(MraidInterface.get_MAX_SIZE_name(MraidInterface.MAX_SIZE.WIDTH), "" + AdSizeUtilities.devicePixelToMraidPoint(metrics.widthPixels, getContext()));
					maxSize.put(MraidInterface.get_MAX_SIZE_name(MraidInterface.MAX_SIZE.HEIGHT), "" + AdSizeUtilities.devicePixelToMraidPoint(metrics.heightPixels - getStatusBarHeight(), getContext()));
					injectJavaScript("mraid.setMaxSize(" + maxSize.toString() + ");");
				}
				catch(Exception ex)
				{
					adLog.log(MASTAdLog.LOG_LEVEL_ERROR, "onPageStarted", "Error setting max size information.");
				}
			}
			
			super.onPageStarted(view, url, favicon);
		}

		@Override
		public void onPageFinished(WebView view, String url)
		{	
			//if(isAutoCollapse) setAdVisibility(View.VISIBLE);
			
			MASTAdDelegate delegate = adViewContainer.getAdDelegate();
			if (delegate != null)
			{
				MASTAdDelegate.AdDownloadEventHandler downloadHandler = delegate.getAdDownloadHandler(); 
				if ( downloadHandler != null)
				{
					downloadHandler.onDownloadEnd((MASTAdView)adViewContainer);
				}
			}

			if (supportMraid)
			{	
				// setDefaultPosition
				try
				{
					int x = AdSizeUtilities.devicePixelToMraidPoint(adViewContainer.getLeft(), context);
					int y = AdSizeUtilities.devicePixelToMraidPoint(adViewContainer.getTop(), context);
					int w = AdSizeUtilities.devicePixelToMraidPoint(adViewContainer.getWidth(), context);
					int h = AdSizeUtilities.devicePixelToMraidPoint(adViewContainer.getHeight(), context);
					
					JSONObject position = new JSONObject();
					position.put(MraidInterface.get_DEFAULT_POSITION_name(MraidInterface.DEFAULT_POSITION.X), "" + x);
					position.put(MraidInterface.get_DEFAULT_POSITION_name(MraidInterface.DEFAULT_POSITION.Y), "" + y);
					position.put(MraidInterface.get_DEFAULT_POSITION_name(MraidInterface.DEFAULT_POSITION.WIDTH), "" + w);
					position.put(MraidInterface.get_DEFAULT_POSITION_name(MraidInterface.DEFAULT_POSITION.HEIGHT), "" + h);
					injectJavaScript("mraid.setDefaultPosition(" + position.toString() + ");");
				}
				catch(Exception ex)
				{
					adLog.log(MASTAdLog.LOG_LEVEL_ERROR, "onPageStarted", "Error setting default position information.");
				}
				
				// set default resize properties (width/height) 
				initializeResizeProperties(); // XXX should not need this with real mraid 2 samples
				
				
				
				// XXX set state to default here per revised spec???
				
				
				
				// Tell ad everything is ready, trigger state change from loading to default
				mraidInterface.fireReadyEvent();
			}
		}

		
		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
		{
			super.onReceivedError(view, errorCode, description, failingUrl);
		
			MASTAdDelegate delegate = adViewContainer.getAdDelegate();
			if (delegate != null)
			{
				MASTAdDelegate.AdDownloadEventHandler downloadHandler = delegate.getAdDownloadHandler();
				if (downloadHandler != null)
				{
					downloadHandler.onDownloadError((MASTAdView)adViewContainer, description);
				}
			}
		}
	}
}
