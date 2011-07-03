
package com.battlelancer.seriesguide.util;

import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.SeriesGuideData;
import com.battlelancer.seriesguide.provider.SeriesContract.Shows;
import com.battlelancer.seriesguide.ui.ShowsActivity;
import com.battlelancer.thetvdbapi.TheTVDB;

import org.xml.sax.SAXException;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.view.View;
import android.view.ViewStub;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicInteger;

public class UpdateTask extends AsyncTask<Void, Integer, Integer> {

    private static final int UPDATE_SUCCESS = 100;

    private static final int UPDATE_SAXERROR = 102;

    private static final int UPDATE_OFFLINE = 103;

    private static final int UPDATE_INCOMPLETE = 104;

    public String[] mShows;

    public String mFailedShows = "";

    private final ShowsActivity mContext;

    public final AtomicInteger mUpdateCount = new AtomicInteger();

    public UpdateTask(String[] shows, ShowsActivity context) {
        mShows = shows;
        mContext = context;
    }

    public UpdateTask(String[] shows, int index, String failedShows, ShowsActivity context) {
        this(shows, context);
        mUpdateCount.set(index);
        mFailedShows = failedShows;
    }

    @Override
    protected void onPreExecute() {
        if (mContext.mProgressOverlay == null) {
            mContext.mProgressOverlay = ((ViewStub) mContext.findViewById(R.id.stub_update))
                    .inflate();
            mContext.mUpdateProgress = (ProgressBar) mContext
                    .findViewById(R.id.ProgressBarShowListDet);

            final View cancelButton = mContext.mProgressOverlay.findViewById(R.id.overlayCancel);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mContext.onCancelTasks();
                }
            });
        }

        mContext.mUpdateProgress.setIndeterminate(false);
        mContext.mUpdateProgress.setProgress(0);
        mContext.showOverlay(mContext.mProgressOverlay);
    }

    @Override
    protected Integer doInBackground(Void... params) {
        final String[] shows = mShows;
        final AtomicInteger updateCount = mUpdateCount;

        ContentResolver resolver = mContext.getContentResolver();
        int resultCode = UPDATE_SUCCESS;
        String id;

        for (int i = updateCount.get(); i < shows.length; i++) {
            // fail early if cancelled or network connection is lost
            if (isCancelled()) {
                resultCode = UPDATE_INCOMPLETE;
                break;
            }
            if (!SeriesGuideData.isNetworkAvailable(mContext)) {
                resultCode = UPDATE_OFFLINE;
                break;
            }

            publishProgress(i, shows.length + 1);

            id = shows[i];
            for (int itry = 0; itry < 2; itry++) {
                try {
                    TheTVDB.updateShow(id, mContext);
                    break;
                } catch (SAXException saxe) {
                    // failed twice
                    if (itry == 1) {
                        resultCode = UPDATE_SAXERROR;
                        Cursor show = resolver.query(Shows.buildShowUri(id), new String[] {
                            Shows.TITLE
                        }, null, null, null);
                        if (show.moveToFirst()) {
                            String name = show.getString(0);
                            addFailedShow(name);
                        }
                        show.close();

                    }
                }
            }

            updateCount.incrementAndGet();
        }

        publishProgress(shows.length, shows.length + 1);

        // renew FTS3 table
        TheTVDB.onRenewFTSTable(mContext);

        publishProgress(shows.length + 1, shows.length + 1);

        return resultCode;
    }

    @Override
    protected void onPostExecute(Integer result) {
        mContext.setFailedShowsString(mFailedShows);

        switch (result) {
            case UPDATE_SUCCESS:
                AnalyticsUtils.getInstance(mContext).trackEvent("Shows", "Update Task", "Success",
                        0);

                Toast.makeText(mContext, mContext.getString(R.string.update_success),
                        Toast.LENGTH_SHORT).show();
                break;
            case UPDATE_SAXERROR:
                AnalyticsUtils.getInstance(mContext).trackEvent("Shows", "Update Task",
                        "SAX error", 0);

                mContext.showDialog(ShowsActivity.UPDATE_SAXERROR_DIALOG);
                break;
            case UPDATE_OFFLINE:
                AnalyticsUtils.getInstance(mContext).trackEvent("Shows", "Update Task", "Offline",
                        0);

                mContext.showDialog(ShowsActivity.UPDATE_OFFLINE_DIALOG);
                break;
        }

        mContext.updateLatestEpisode();
        mContext.hideOverlay(mContext.mProgressOverlay);
    }

    @Override
    protected void onCancelled() {
        mContext.hideOverlay(mContext.mProgressOverlay);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        final ProgressBar progress = mContext.mUpdateProgress;
        progress.setMax(values[1]);
        progress.setProgress(values[0]);
    }

    private void addFailedShow(String seriesName) {
        if (mFailedShows.length() != 0) {
            mFailedShows += ", ";
        }
        mFailedShows += seriesName;
    }

}
