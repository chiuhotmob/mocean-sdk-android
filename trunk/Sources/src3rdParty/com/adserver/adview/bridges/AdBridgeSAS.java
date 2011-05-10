package com.adserver.adview.bridges;

import com.adserver.adview.Utils;
import com.smartadserver.library.SmartAdServerAd;
import com.smartadserver.library.SmartAdServerAdBannerView;
import com.smartadserver.library.SmartAdServerAdView;
import com.smartadserver.library.SmartAdServerAdBannerView.SmartAdServerAdBannerViewDelegate;
import com.smartadserver.library.SmartAdServerAdView.SmartAdServerAdViewDelegate;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.webkit.WebView;

public class AdBridgeSAS extends AdBridgeAbstract implements SmartAdServerAdViewDelegate, SmartAdServerAdBannerViewDelegate {

	public AdBridgeSAS(Context context, WebView view, String campaignId,
			String externalParams, String trackUrl) {
		super(context, view, campaignId, externalParams, trackUrl);
	}

	public void run() {
		if (Build.VERSION.SDK_INT<8)
		{
			DownloadError("[ERROR]AdBridgeSAS: SDK version < 8");
			return;
		}
		try
		{
			String siteid = /*"19369";//*/Utils.scrape(externalParams, "<param name=\"siteid\">", "</param>");
			String pageid = /*"136527";//*/Utils.scrape(externalParams, "<param name=\"pageid\">", "</param>");
			String formatid = /*"5919";//*/Utils.scrape(externalParams, "<param name=\"formatid\">", "</param>");
					
			SmartAdServerAd mAdBanner = new SmartAdServerAd();
			SmartAdServerAdBannerView mAdBannerView = new SmartAdServerAdBannerView(context);
			
			mAdBannerView.setDelegate(this);		
			mAdBannerView.setBannerDelegate(this);
			
			mAdBanner.init(mAdBannerView, (Activity)context, siteid, pageid, formatid, "M", "null", null);
			
			mAdBannerView.setLayoutParams(view.getLayoutParams());
			view.addView(mAdBannerView);
		} catch (Exception e) {
			DownloadError(e.getMessage());
		}
	}

	@Override
	public void onAdDownloadComplete(SmartAdServerAdView arg0) {
		DownloadEnd();		
	}

	@Override
	public void onAdDownloadFailed(SmartAdServerAdView arg0) {
		DownloadError("[ERROR] AdBridgeSAS: onAdDownloadFailed");
	}

	@Override
	public void onAdImageDownloadComplete(SmartAdServerAdView arg0, boolean arg1) {		
	}

	@Override
	public void onAdImageDownloadFailed(SmartAdServerAdView arg0, boolean arg1) {
		//DownloadError("");		
	}

	@Override
	public void onClick(SmartAdServerAdView arg0) {
		Click();		
	}

	@Override
	public void onExpand(SmartAdServerAdBannerView arg0) {
		
	}

	@Override
	public void onUnexpand(SmartAdServerAdBannerView arg0) {
		
	}

}