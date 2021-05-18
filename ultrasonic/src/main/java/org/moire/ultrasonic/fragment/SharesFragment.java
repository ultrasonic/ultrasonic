package org.moire.ultrasonic.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jetbrains.annotations.NotNull;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException;
import org.moire.ultrasonic.domain.Share;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.service.OfflineException;
import org.moire.ultrasonic.subsonic.DownloadHandler;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.LoadingTask;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.TimeSpan;
import org.moire.ultrasonic.util.TimeSpanPicker;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.ShareAdapter;

import java.util.List;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * Displays the shares in the media library
 */
public class SharesFragment extends Fragment {

    private SwipeRefreshLayout refreshSharesListView;
    private ListView sharesListView;
    private View emptyTextView;
    private ShareAdapter shareAdapter;

    private final Lazy<DownloadHandler> downloadHandler = inject(DownloadHandler.class);
    private CancellationToken cancellationToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.select_share, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();

        refreshSharesListView = view.findViewById(R.id.select_share_refresh);
        sharesListView = view.findViewById(R.id.select_share_list);

        refreshSharesListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                load(true);
            }
        });

        emptyTextView = view.findViewById(R.id.select_share_empty);
        sharesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Share share = (Share) parent.getItemAtPosition(position);

                if (share == null)
                {
                    return;
                }

                Bundle bundle = new Bundle();
                bundle.putString(Constants.INTENT_EXTRA_NAME_SHARE_ID, share.getId());
                bundle.putString(Constants.INTENT_EXTRA_NAME_SHARE_NAME, share.getName());
                Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
            }
        });
        registerForContextMenu(sharesListView);
        FragmentTitle.Companion.setTitle(this, R.string.button_bar_shares);

        load(false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final boolean refresh)
    {
        BackgroundTask<List<Share>> task = new FragmentBackgroundTask<List<Share>>(getActivity(), true, refreshSharesListView, cancellationToken)
        {
            @Override
            protected List<Share> doInBackground() throws Throwable
            {
                MusicService musicService = MusicServiceFactory.getMusicService();
                return musicService.getShares(refresh);
            }

            @Override
            protected void done(List<Share> result)
            {
                sharesListView.setAdapter(shareAdapter = new ShareAdapter(getContext(), result));
                emptyTextView.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
            }
        };
        task.execute();
    }

    @Override
    public void onCreateContextMenu(@NotNull ContextMenu menu, @NotNull View view, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.select_share_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
        if (info == null) return false;

        Share share = (Share) sharesListView.getItemAtPosition(info.position);
        if (share == null || share.getId() == null) return false;

        int itemId = menuItem.getItemId();
        if (itemId == R.id.share_menu_pin) {
            downloadHandler.getValue().downloadShare(this, share.getId(), share.getName(), true, true, false, false, true, false, false);
        } else if (itemId == R.id.share_menu_unpin) {
            downloadHandler.getValue().downloadShare(this, share.getId(), share.getName(), false, false, false, false, true, false, true);
        } else if (itemId == R.id.share_menu_download) {
            downloadHandler.getValue().downloadShare(this, share.getId(), share.getName(), false, false, false, false, true, false, false);
        } else if (itemId == R.id.share_menu_play_now) {
            downloadHandler.getValue().downloadShare(this, share.getId(), share.getName(), false, false, true, false, false, false, false);
        } else if (itemId == R.id.share_menu_play_shuffled) {
            downloadHandler.getValue().downloadShare(this, share.getId(), share.getName(), false, false, true, true, false, false, false);
        } else if (itemId == R.id.share_menu_delete) {
            deleteShare(share);
        } else if (itemId == R.id.share_info) {
            displayShareInfo(share);
        } else if (itemId == R.id.share_update_info) {
            updateShareInfo(share);
        } else {
            return super.onContextItemSelected(menuItem);
        }
        return true;
    }

    private void deleteShare(final Share share)
    {
        new AlertDialog.Builder(getContext()).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.common_confirm).setMessage(getResources().getString(R.string.delete_playlist, share.getName())).setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                new LoadingTask<Void>(getActivity(), refreshSharesListView, cancellationToken)
                {
                    @Override
                    protected Void doInBackground() throws Throwable
                    {
                        MusicService musicService = MusicServiceFactory.getMusicService();
                        musicService.deleteShare(share.getId());
                        return null;
                    }

                    @Override
                    protected void done(Void result)
                    {
                        shareAdapter.remove(share);
                        shareAdapter.notifyDataSetChanged();
                        Util.toast(getContext(), getResources().getString(R.string.menu_deleted_share, share.getName()));
                    }

                    @Override
                    protected void error(Throwable error)
                    {
                        String msg;
                        msg = error instanceof OfflineException || error instanceof ApiNotSupportedException ? getErrorMessage(error) : String.format("%s %s", getResources().getString(R.string.menu_deleted_share_error, share.getName()), getErrorMessage(error));

                        Util.toast(getContext(), msg, false);
                    }
                }.execute();
            }

        }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void displayShareInfo(final Share share)
    {
        final TextView textView = new TextView(getContext());
        textView.setPadding(5, 5, 5, 5);

        final Spannable message = new SpannableString("Owner: " + share.getUsername() +
            "\nComments: " + ((share.getDescription() == null) ? "" : share.getDescription()) +
            "\nURL: " + share.getUrl() +
            "\nEntry Count: " + share.getEntries().size() +
            "\nVisit Count: " + share.getVisitCount() +
            ((share.getCreated() == null) ? "" : ("\nCreation Date: " + share.getCreated().replace('T', ' '))) +
            ((share.getLastVisited() == null) ? "" : ("\nLast Visited Date: " + share.getLastVisited().replace('T', ' '))) +
            ((share.getExpires() == null) ? "" : ("\nExpiration Date: " + share.getExpires().replace('T', ' '))));

        Linkify.addLinks(message, Linkify.WEB_URLS);
        textView.setText(message);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        new AlertDialog.Builder(getContext()).setTitle("Share Details").setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setView(textView).show();
    }

    private void updateShareInfo(final Share share)
    {
        View dialogView = getLayoutInflater().inflate(R.layout.share_details, null);
        if (dialogView == null)
        {
            return;
        }

        final EditText shareDescription = dialogView.findViewById(R.id.share_description);
        final TimeSpanPicker timeSpanPicker = dialogView.findViewById(R.id.date_picker);

        shareDescription.setText(share.getDescription());

        CheckBox hideDialogCheckBox = dialogView.findViewById(R.id.hide_dialog);
        CheckBox saveAsDefaultsCheckBox = dialogView.findViewById(R.id.save_as_defaults);
        CheckBox noExpirationCheckBox = dialogView.findViewById(R.id.timeSpanDisableCheckBox);

        noExpirationCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b)
            {
                timeSpanPicker.setEnabled(!b);
            }
        });

        noExpirationCheckBox.setChecked(true);

        timeSpanPicker.setTimeSpanDisableText(getResources().getText(R.string.no_expiration));

        hideDialogCheckBox.setVisibility(View.GONE);
        saveAsDefaultsCheckBox.setVisibility(View.GONE);

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getContext());

        alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
        alertDialog.setTitle(R.string.playlist_update_info);
        alertDialog.setView(dialogView);
        alertDialog.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                new LoadingTask<Void>(getActivity(), refreshSharesListView, cancellationToken)
                {
                    @Override
                    protected Void doInBackground() throws Throwable
                    {
                        long millis = timeSpanPicker.getTimeSpan().getTotalMilliseconds();

                        if (millis > 0)
                        {
                            millis = TimeSpan.getCurrentTime().add(millis).getTotalMilliseconds();
                        }

                        Editable shareDescriptionText = shareDescription.getText();
                        String description = shareDescriptionText != null ? shareDescriptionText.toString() : null;

                        MusicService musicService = MusicServiceFactory.getMusicService();
                        musicService.updateShare(share.getId(), description, millis);
                        return null;
                    }

                    @Override
                    protected void done(Void result)
                    {
                        load(true);
                        Util.toast(getContext(), getResources().getString(R.string.playlist_updated_info, share.getName()));
                    }

                    @Override
                    protected void error(Throwable error)
                    {
                        String msg;
                        msg = error instanceof OfflineException || error instanceof ApiNotSupportedException ? getErrorMessage(error) : String.format("%s %s", getResources().getString(R.string.playlist_updated_info_error, share.getName()), getErrorMessage(error));

                        Util.toast(getContext(), msg, false);
                    }
                }.execute();
            }
        });

        alertDialog.setNegativeButton(R.string.common_cancel, null);
        alertDialog.show();
    }
}
