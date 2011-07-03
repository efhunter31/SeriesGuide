
package com.battlelancer.seriesguide;

import com.battlelancer.seriesguide.provider.SeriesContract.Shows;
import com.battlelancer.seriesguide.provider.SeriesGuideDatabase;
import com.battlelancer.seriesguide.ui.BaseActivity;
import com.battlelancer.seriesguide.util.AnalyticsUtils;
import com.battlelancer.seriesguide.util.UIUtils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBar;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * Import/export DB activity.
 * 
 * @author ccollins
 * @author trottmann.uwe
 */
public class BackupDelete extends BaseActivity {

    private Button exportDbToSdButton;

    private Button importDbFromSdButton;

    private ExportDatabaseTask mExportTask;

    private ImportDatabaseTask mImportTask;

    private ProgressDialog importProgress;

    private ProgressDialog exportProgress;

    public static final String TAG = "BackupDelete";

    private static final int EXPORT_DIALOG = 0;

    private static final int IMPORT_DIALOG = 1;

    private static final int EXPORT_PROGRESS = 3;

    private static final int IMPORT_PROGRESS = 4;

    public void fireTrackerEvent(String label) {
        AnalyticsUtils.getInstance(this).trackEvent(TAG, "Click", label, 0);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.backup);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getString(R.string.backup));
        actionBar.setDisplayShowTitleEnabled(true);

        exportDbToSdButton = (Button) findViewById(R.id.ButtonExportDBtoSD);
        exportDbToSdButton.setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                showDialog(EXPORT_DIALOG);
            }
        });

        importDbFromSdButton = (Button) findViewById(R.id.ButtonImportDBfromSD);
        importDbFromSdButton.setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                showDialog(IMPORT_DIALOG);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        AnalyticsUtils.getInstance(this).trackPageView("/BackupDelete");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onCancelTasks();
    }

    private void onCancelTasks() {
        if (mImportTask != null && mImportTask.getStatus() == AsyncTask.Status.RUNNING) {
            mImportTask.cancel(true);
            mImportTask = null;
        }
        if (mExportTask != null && mExportTask.getStatus() == AsyncTask.Status.RUNNING) {
            mExportTask.cancel(true);
            mExportTask = null;
        }
    }

    private class ExportDatabaseTask extends AsyncTask<Void, Void, String> {

        // can use UI thread here
        @Override
        protected void onPreExecute() {
            showDialog(EXPORT_PROGRESS);
        }

        // automatically done on worker thread (separate from UI thread)
        @Override
        protected String doInBackground(final Void... args) {
            File dbFile = getApplication().getDatabasePath(SeriesGuideDatabase.DATABASE_NAME);

            File exportDir = new File(Environment.getExternalStorageDirectory(),
                    "seriesguidebackup");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            File file = new File(exportDir, dbFile.getName());

            if (isCancelled()) {
                return null;
            }

            String errorMsg = null;
            try {
                file.createNewFile();
                FileUtil.copyFile(dbFile, file);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
                errorMsg = e.getMessage();
            }
            return errorMsg;
        }

        // can use UI thread here
        @Override
        protected void onPostExecute(final String errorMsg) {
            if (exportProgress.isShowing()) {
                exportProgress.dismiss();
            }
            if (errorMsg == null) {
                // track event
                AnalyticsUtils.getInstance(BackupDelete.this).trackEvent(TAG, "Backup", "Success",
                        0);

                Toast.makeText(BackupDelete.this, getString(R.string.backup_success),
                        Toast.LENGTH_SHORT).show();
            } else {
                // track event
                AnalyticsUtils.getInstance(BackupDelete.this)
                        .trackEvent(TAG, "Backup", errorMsg, 0);

                Toast.makeText(BackupDelete.this,
                        getString(R.string.backup_failed) + " - " + errorMsg, Toast.LENGTH_LONG)
                        .show();
            }
            setResult(RESULT_OK);
            finish();
        }
    }

    private class ImportDatabaseTask extends AsyncTask<Void, Void, String> {

        @Override
        protected void onPreExecute() {
            showDialog(IMPORT_PROGRESS);
        }

        // could pass the params used here in AsyncTask<String, Void, String> -
        // but not being re-used
        @Override
        protected String doInBackground(final Void... args) {

            File dbBackupFile = new File(Environment.getExternalStorageDirectory()
                    + "/seriesguidebackup/seriesdatabase");
            if (!dbBackupFile.exists()) {
                return getString(R.string.import_failed_nofile);
            } else if (!dbBackupFile.canRead()) {
                return getString(R.string.import_failed_noread);
            }

            if (isCancelled()) {
                return null;
            }

            File dbFile = getApplication().getDatabasePath(SeriesGuideDatabase.DATABASE_NAME);
            
            getApplication().deleteDatabase(SeriesGuideDatabase.DATABASE_NAME);

            try {
                dbFile.createNewFile();
                FileUtil.copyFile(dbBackupFile, dbFile);

                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit()
                        .putBoolean(SeriesGuidePreferences.KEY_DATABASEIMPORTED, true).commit();
                getContentResolver().notifyChange(Shows.CONTENT_URI, null);

                return null;
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(final String errMsg) {
            if (importProgress.isShowing()) {
                importProgress.dismiss();
            }
            if (errMsg == null) {
                // track event
                AnalyticsUtils.getInstance(BackupDelete.this).trackEvent(TAG, "Import", "Success",
                        0);

                Toast.makeText(BackupDelete.this, getString(R.string.import_success),
                        Toast.LENGTH_SHORT).show();
            } else {
                // track event
                AnalyticsUtils.getInstance(BackupDelete.this).trackEvent(TAG, "Import", errMsg, 0);

                Toast.makeText(BackupDelete.this,
                        getString(R.string.import_failed) + " - " + errMsg, Toast.LENGTH_LONG)
                        .show();
            }
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case EXPORT_DIALOG:
                return new AlertDialog.Builder(BackupDelete.this)
                        .setMessage(getString(R.string.backup_question))
                        .setPositiveButton(getString(R.string.backup_yes),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface arg0, int arg1) {
                                        if (UIUtils.isExtStorageAvailable()) {
                                            // track event
                                            fireTrackerEvent("Do Backup");

                                            mExportTask = new ExportDatabaseTask();
                                            mExportTask.execute();
                                        } else {
                                            // track event
                                            fireTrackerEvent("Don't Backup");

                                            Toast.makeText(BackupDelete.this,
                                                    getString(R.string.backup_failed_nosd),
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }).setNegativeButton(getString(R.string.backup_no), null).create();
            case IMPORT_DIALOG:
                return new AlertDialog.Builder(BackupDelete.this)
                        .setMessage(getString(R.string.import_question))
                        .setPositiveButton(getString(R.string.import_yes),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface arg0, int arg1) {
                                        if (UIUtils.isExtStorageAvailable()) {
                                            // track event
                                            fireTrackerEvent("Do Import");

                                            mImportTask = new ImportDatabaseTask();
                                            mImportTask.execute();
                                        } else {
                                            // track event
                                            fireTrackerEvent("Don't Import");

                                            Toast.makeText(BackupDelete.this,
                                                    getString(R.string.import_failed_nosd),
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }).setNegativeButton(getString(R.string.import_no), null).create();
            case EXPORT_PROGRESS:
                exportProgress = new ProgressDialog(BackupDelete.this);
                exportProgress.setMessage(getString(R.string.backup_inprogress));
                return exportProgress;
            case IMPORT_PROGRESS:
                importProgress = new ProgressDialog(BackupDelete.this);
                importProgress.setMessage(getString(R.string.import_inprogress));
                return importProgress;
        }
        return null;
    }
}
