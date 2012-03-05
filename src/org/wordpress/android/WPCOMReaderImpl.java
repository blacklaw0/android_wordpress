package org.wordpress.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.wordpress.android.models.Blog;

import android.app.Activity;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WPCOMReaderImpl extends WPCOMReaderBase {
	/** Called when the activity is first created. */
	private String loginURL = "";
//	private boolean isPage = false;
	public WebView wv;
	public String topicsID;
	//private String cachedTopicsPage = null;
	private String cachedDetailPage = null;
	private ChangePageListener onChangePageListener;
	private PostSelectedListener onPostSelectedListener;
	private UpdateTopicListener onUpdateTopicListener;
	public TextView topicTV;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
		View v = inflater.inflate(R.layout.reader_wpcom, container, false);
		if (WordPress.wpDB == null)
			WordPress.wpDB = new WordPressDB(getActivity().getApplicationContext());
		if (WordPress.currentBlog == null) {
			try {
				WordPress.currentBlog = new Blog(
						WordPress.wpDB.getLastBlogID(getActivity().getApplicationContext()), getActivity().getApplicationContext());
			} catch (Exception e) {
				Toast.makeText(getActivity().getApplicationContext(), getResources().getText(R.string.blog_not_found), Toast.LENGTH_SHORT).show();
				getActivity().finish();
			}
		}
		
		topicTV = (TextView) v.findViewById(R.id.topic_title);

		//this.setTitle(getResources().getText(R.string.reader)); //FIXME: set the title of the screen here
		wv = (WebView) v.findViewById(R.id.webView);
		wv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		wv.addJavascriptInterface( new JavaScriptInterface(getActivity().getApplicationContext()), interfaceNameForJS );
		this.setDefaultWebViewSettings(wv);
		new loadReaderTask().execute(null, null, null, null);
		
		RelativeLayout rl = (RelativeLayout) v.findViewById(R.id.topicSelector);
		rl.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (onChangePageListener != null)
					onChangePageListener.onChangePage(0);
			}
		});
		
		Button refreshButton = (Button) v.findViewById(R.id.action_refresh);
		refreshButton
		.setOnClickListener(new ImageButton.OnClickListener() {
			public void onClick(View v) {
				//startRotatingRefreshIcon();
				wv.reload();
				new Thread(new Runnable() {
					public void run() {
						// refresh stat
						try {
							HttpClient httpclient = new DefaultHttpClient();
							HttpProtocolParams.setUserAgent(httpclient.getParams(),
									"wp-android");
							String readerURL = Constants.readerURL + "/?template=stats&stats_name=home_page_refresh";
							if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == 4) {
								readerURL += "&per_page=20";
							}

							httpclient.execute(new HttpGet(readerURL));
						} catch (Exception e) {
							// oh well
						}
					}
				}).start();

			}
		});
		
		return v;
    }
	
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			// check that the containing activity implements our callback
			onChangePageListener= (ChangePageListener) activity;
			onPostSelectedListener = (PostSelectedListener) activity;
			onUpdateTopicListener = (UpdateTopicListener) activity;
		} catch (ClassCastException e) {
			activity.finish();
			throw new ClassCastException(activity.toString()
					+ " must implement Callback");
		}
	}
	
	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }
	
	private class loadReaderTask extends AsyncTask<String, Void, Vector<?>> {

		@Override 
		protected void onPreExecute() {
			//startRotatingRefreshIcon();
		}
		
		protected void onPostExecute(Vector<?> result) {
			
			//Read the WordPress.com cookies from the wv and pass them to the connections below!
			CookieManager cookieManager = CookieManager.getInstance();
			final String cookie = cookieManager.getCookie("wordpress.com");
			//stopRotatingRefreshIcon();
      	
			new Thread(new Runnable() {
				public void run() {
					try {
						HttpClient httpclient = new DefaultHttpClient();
						HttpProtocolParams.setUserAgent(httpclient.getParams(),	"wp-android");
						
						String readerURL = Constants.readerURL + "/?template=stats&stats_name=home_page";
						HttpGet httpGet = new HttpGet(readerURL);
						httpGet.setHeader("Cookie", cookie);
						httpclient.execute(httpGet);

						//Cache the Topics page
						String hybURL = WPCOMReaderImpl.this.getAuthorizeHybridURL(Constants.readerTopicsURL);
   	    		        //WPCOMReaderImpl.this.cachedTopicsPage = cachePage(hybURL, cookie);
   	    		        
						//Cache the DAtil page
						hybURL = WPCOMReaderImpl.this.getAuthorizeHybridURL(Constants.readerDetailURL);
   	    		        WPCOMReaderImpl.this.cachedDetailPage = cachePage(hybURL, cookie);
						
					} catch (Exception e) {
						// oh well
						e.printStackTrace();
					}
				}
			}).start();
		}

		private String cachePage(String hybURL, String cookie) {
			HttpClient httpclient = new DefaultHttpClient();

			try {
				HttpProtocolParams.setUserAgent(httpclient.getParams(), "wp-android");
				HttpGet request = new HttpGet(hybURL);
				request.setHeader("Cookie", cookie);
				HttpResponse response = httpclient.execute(request);

				// Check if server response is valid
				StatusLine status = response.getStatusLine();
				if (status.getStatusCode() != 200) {
					throw new IOException("Invalid response from server when caching the page: " + status.toString());
				}

				// Pull content stream from response
				HttpEntity entity = response.getEntity();
				InputStream inputStream = (InputStream) entity.getContent();

				ByteArrayOutputStream content = new ByteArrayOutputStream();

				// Read response into a buffered stream
				int readBytes = 0;
				byte[] sBuffer = new byte[512];
				while ((readBytes = inputStream.read(sBuffer)) != -1) {
					content.write(sBuffer, 0, readBytes);
				}
				// Return result from buffered stream
				String dataAsString = new String(content.toByteArray());
				return dataAsString;
			} catch (Exception e) {
				// oh well
				Log.d("Error while caching the page" + hybURL, e.getLocalizedMessage());
				return null;

			} finally {
				// When HttpClient instance is no longer needed,
				// shut down the connection manager to ensure
				// immediate deallocation of all system resources
				httpclient.getConnectionManager().shutdown();
			}
		}

		
		@Override
		protected Vector<?> doInBackground(String... args) {

			if (WordPress.currentBlog == null) {
				try {
					WordPress.currentBlog = new Blog(
							WordPress.wpDB.getLastBlogID(getActivity().getApplicationContext()), getActivity().getApplicationContext());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			loginURL = WordPress.currentBlog.getUrl()
					.replace("xmlrpc.php", "wp-login.php");
			if (WordPress.currentBlog.getUrl().lastIndexOf("/") != -1)
				loginURL = WordPress.currentBlog.getUrl().substring(0, WordPress.currentBlog.getUrl().lastIndexOf("/")) + "/wp-login.php";
			else
				loginURL = WordPress.currentBlog.getUrl().replace("xmlrpc.php", "wp-login.php");
			
			String readerURL = WPCOMReaderImpl.this.getAuthorizeHybridURL(Constants.readerURL_v3);
		
			if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == 4) {
				if( readerURL.contains("?") )
					readerURL += "&per_page=20";
				else 
					readerURL += "?per_page=20";
			}
			
			try {
				String responseContent = "<head>"
						+ "<script type=\"text/javascript\">"
						+ "function submitform(){document.loginform.submit();} </script>"
						+ "</head>"
						+ "<body onload=\"submitform()\">"
						+ "<form style=\"visibility:hidden;\" name=\"loginform\" id=\"loginform\" action=\""
						+ loginURL
						+ "\" method=\"post\">"
						+ "<input type=\"text\" name=\"log\" id=\"user_login\" value=\""
						+ WordPress.currentBlog.getUsername()
						+ "\"/></label>"
						+ "<input type=\"password\" name=\"pwd\" id=\"user_pass\" value=\""
						+ WordPress.currentBlog.getPassword()
						+ "\" /></label>"
						+ "<input type=\"submit\" name=\"wp-submit\" id=\"wp-submit\" value=\"Log In\" />"
						+ "<input type=\"hidden\" name=\"redirect_to\" value=\""
						+ readerURL + "\" />" + "</form>" + "</body>";

				wv.setWebViewClient(new WebViewClient() {
					@Override
					public boolean shouldOverrideUrlLoading(WebView view, String url) {
						if( url.equalsIgnoreCase( Constants.readerDetailURL ) ) {
							onPostSelectedListener.onPostSelected(url, WPCOMReaderImpl.this.cachedDetailPage);
							return true;
						}
						view.loadUrl(url);
						return false;
					}
					
					@Override
					public void onPageFinished(WebView view, String url) {
					}
				});


				wv.setWebChromeClient(new WebChromeClient() {
					public void onProgressChanged(WebView view, int progress) {
						//WPCOMReaderImpl.this.setTitle("Loading...");
						//WPCOMReaderImpl.this.setProgress(progress * 100);

						if (progress == 100) {
							//WPCOMReaderImpl.this.setTitle(getResources().getText(R.string.reader));
						}
					}
				});
		
				wv.loadData(Uri.encode(responseContent), "text/html", HTTP.UTF_8);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			return null;

		}

	}
	
	//The JS calls this method on first loading
		public void setSelectedTopicFromJS(String topicsID) {
			this.topicsID = topicsID;
			onUpdateTopicListener.onUpdateTopic(topicsID);
		}
		
		public void setTitleFromJS(final String newTopicName) {
			getActivity().runOnUiThread(new Runnable() {
			     public void run() {
			    	 if (newTopicName != null) {
			    		 topicTV.setText(newTopicName);
			    	 }
			    }
			});
		}
	
	public interface UpdateTopicListener {
		public void onUpdateTopic(String topicID);
	}
		
	public interface ChangePageListener {
		public void onChangePage(int position);
	}
	
	public interface PostSelectedListener {
		public void onPostSelected(String requestedURL, String cachedPage);
	}
	
}