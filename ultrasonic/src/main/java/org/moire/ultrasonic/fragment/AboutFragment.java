package org.moire.ultrasonic.fragment;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jetbrains.annotations.NotNull;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.util.Util;

/**
 * Displays online help and about information in a WebView
 */
public class AboutFragment extends Fragment {

    private WebView webView;
    private ImageView backButton;
    private ImageView forwardButton;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.help, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        swipeRefresh = view.findViewById(R.id.help_refresh);
        swipeRefresh.setEnabled(false);

        webView = view.findViewById(R.id.help_contents);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new HelpClient());

        if (savedInstanceState != null)
        {
            webView.restoreState(savedInstanceState);
        }
        else
        {
            webView.loadUrl(getResources().getString(R.string.help_url));
        }

        backButton = view.findViewById(R.id.help_back);
        backButton.setOnClickListener(new Button.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                webView.goBack();
            }
        });

        ImageView stopButton = view.findViewById(R.id.help_stop);
        stopButton.setOnClickListener(new Button.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                webView.stopLoading();
                swipeRefresh.setRefreshing(false);
            }
        });

        forwardButton = view.findViewById(R.id.help_forward);
        forwardButton.setOnClickListener(new Button.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                webView.goForward();
            }
        });

        // TODO: Nicer Back key handling?
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        webView.setOnKeyListener( new View.OnKeyListener()
        {
            @Override
            public boolean onKey( View v, int keyCode, KeyEvent event )
            {
                if (keyCode == KeyEvent.KEYCODE_BACK)
                {
                    if (webView.canGoBack())
                    {
                        webView.goBack();
                        return true;
                    }
                }
                return false;
            }
        } );
    }

    @Override
    public void onSaveInstanceState(@NotNull Bundle state)
    {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    private final class HelpClient extends WebViewClient
    {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            swipeRefresh.setRefreshing(true);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url)
        {
            swipeRefresh.setRefreshing(false);
            String versionName = Util.getVersionName(getContext());
            String title = String.format("%s (%s)", view.getTitle(), versionName);

            FragmentTitle.Companion.setTitle(AboutFragment.this, title);

            backButton.setEnabled(view.canGoBack());
            forwardButton.setEnabled(view.canGoForward());
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
        {
            Util.toast(getContext(), description);
        }
    }
}
