/*
 * Copyright 2012 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.battlelancer.seriesguide.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Window;
import com.google.analytics.tracking.android.EasyTracker;
import com.uwetrottmann.seriesguide.R;

/**
 * Users can search for a movie, display detailed information and then check in
 * with trakt or GetGlue.
 */
public class MoviesActivity extends BaseTopActivity {

    private static final String TAG = "Movies";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // MovieSearchFragment needs a progress bar
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.movies);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getString(R.string.movies));
        actionBar.setIcon(R.drawable.ic_action_movie);

        if (savedInstanceState == null) {
            Fragment f = new MovieSearchFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.movies_container, f);
            ft.commit();
        }
    }

    @Override
    protected void fireTrackerEvent(String label) {
        EasyTracker.getTracker().sendEvent(TAG, "Action Item", label, (long) 0);
    }
}
