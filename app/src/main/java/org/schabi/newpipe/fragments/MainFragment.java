package org.schabi.newpipe.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener {
    private FragmentMainBinding binding;
    private SelectedTabsPagerAdapter pagerAdapter;

    private final List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;

    private SharedPreferences prefs;
    private boolean youtubeRestrictedModeEnabled;
    private String youtubeRestrictedModeEnabledKey;

    // top red bar (toolbar) hiding
    private boolean toolbarHidden = false;
    private int savedTopMargin = 0;

    // keeps the pill bar above the mini player
    private BottomSheetBehavior<View> sheetBehavior;
    private BottomSheetBehavior.BottomSheetCallback sheetCallback;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                setupTabs();
            } else {
                hasTabsChanged = true;
            }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        binding = FragmentMainBinding.bind(rootView);

        binding.mainTabLayout.setupWithViewPager(binding.pager);
        binding.mainTabLayout.addOnTabSelectedListener(this);

        binding.mainMenuButton.setOnClickListener(v -> openDrawer());
        binding.mainSearchButton.setOnClickListener(v -> openSearch());

        setupTabs();
    }

    @Override
    public void onResume() {
        super.onResume();

        final boolean newYoutubeRestrictedModeEnabled =
                prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        if (youtubeRestrictedModeEnabled != newYoutubeRestrictedModeEnabled || hasTabsChanged) {
            youtubeRestrictedModeEnabled = newYoutubeRestrictedModeEnabled;
            setupTabs();
        }

        hideToolbar();
        attachSheetCallback();
    }

    @Override
    public void onPause() {
        super.onPause();
        detachSheetCallback();
        showToolbar();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
        if (binding != null) {
            binding.pager.setAdapter(null);
            binding = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Toolbar (top red bar) and pill bar helpers
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Hides the red toolbar so the content can use the full screen.
     * The status bar keeps its color from the theme.
     */
    private void hideToolbar() {
        final ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        final View holder = activity.findViewById(R.id.fragment_holder);
        if (holder != null && holder.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) holder.getLayoutParams();
            if (!toolbarHidden) {
                savedTopMargin = lp.topMargin;
            }
            lp.topMargin = 0;
            holder.setLayoutParams(lp);
        }
        toolbarHidden = true;
    }

    /**
     * Brings the toolbar back, because all other screens (search, video, channel...) need it.
     */
    private void showToolbar() {
        if (!toolbarHidden) {
            return;
        }
        final View holder = activity.findViewById(R.id.fragment_holder);
        if (holder != null && holder.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) holder.getLayoutParams();
            lp.topMargin = savedTopMargin;
            holder.setLayoutParams(lp);
        }
        final ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        }
        toolbarHidden = false;
    }

    private void openDrawer() {
        final View drawer = activity.findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.getParent() instanceof DrawerLayout) {
            ((DrawerLayout) drawer.getParent()).openDrawer(GravityCompat.START);
        }
    }

    private void openSearch() {
        try {
            NavigationHelper.openSearchFragment(getFM(),
                    ServiceHelper.getSelectedServiceId(activity), "");
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
        }
    }

    private void attachSheetCallback() {
        final View sheet = activity.findViewById(R.id.fragment_player_holder);
        if (sheet == null) {
            return;
        }
        try {
            sheetBehavior = BottomSheetBehavior.from(sheet);
        } catch (final IllegalArgumentException e) {
            sheetBehavior = null;
            return;
        }
        sheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
                updatePillMargin();
            }

            @Override
            public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
                // not needed
            }
        };
        sheetBehavior.addBottomSheetCallback(sheetCallback);
        updatePillMargin();
    }

    private void detachSheetCallback() {
        if (sheetBehavior != null && sheetCallback != null) {
            sheetBehavior.removeBottomSheetCallback(sheetCallback);
        }
        sheetCallback = null;
        sheetBehavior = null;
    }

    /**
     * Lifts the pill bar above the mini player when the mini player is visible.
     */
    private void updatePillMargin() {
        if (binding == null || sheetBehavior == null) {
            return;
        }
        final int state = sheetBehavior.getState();
        final int lift;
        if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            lift = Math.max(sheetBehavior.getPeekHeight(), 0);
        } else if (state == BottomSheetBehavior.STATE_HIDDEN) {
            lift = 0;
        } else {
            return;
        }
        final int base = Math.round(16 * getResources().getDisplayMetrics().density);
        final RelativeLayout.LayoutParams lp =
                (RelativeLayout.LayoutParams) binding.mainPillBar.getLayoutParams();
        lp.bottomMargin = base + lift;
        binding.mainPillBar.setLayoutParams(lp);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        inflater.inflate(R.menu.menu_main_fragment, menu);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            openSearch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void setupTabs() {
        tabsList.clear();
        tabsList.addAll(tabsManager.getTabs());

        if (pagerAdapter == null || !pagerAdapter.sameTabs(tabsList)) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList);
        }

        binding.pager.setAdapter(null);
        binding.pager.setAdapter(pagerAdapter);

        updateTabsIconAndDescription();
        updateTitleForTab(binding.pager.getCurrentItem());

        hasTabsChanged = false;
    }

    private void updateTabsIconAndDescription() {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = binding.mainTabLayout.getTabAt(i);
            if (tabToSet != null) {
                final Tab tab = tabsList.get(i);
                tabToSet.setIcon(tab.getTabIconRes(requireContext()));
                tabToSet.setContentDescription(tab.getTabName(requireContext()));
            }
        }
        shrinkTabIcons();
    }

    /**
     * Makes the tab icons smaller (20dp) so they fit the thin pill bar.
     */
    private void shrinkTabIcons() {
        if (binding == null || !(binding.mainTabLayout.getChildAt(0) instanceof ViewGroup)) {
            return;
        }
        final int size = Math.round(20 * getResources().getDisplayMetrics().density);
        final ViewGroup strip = (ViewGroup) binding.mainTabLayout.getChildAt(0);
        for (int i = 0; i < strip.getChildCount(); i++) {
            if (!(strip.getChildAt(i) instanceof ViewGroup)) {
                continue;
            }
            final ViewGroup tabView = (ViewGroup) strip.getChildAt(i);
            for (int j = 0; j < tabView.getChildCount(); j++) {
                final View child = tabView.getChildAt(j);
                if (child instanceof ImageView) {
                    final ViewGroup.LayoutParams lp = child.getLayoutParams();
                    lp.width = size;
                    lp.height = size;
                    child.setLayoutParams(lp);
                }
            }
        }
    }

    private void updateTitleForTab(final int tabPosition) {
        setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
    }

    public void commitPlaylistTabs() {
        pagerAdapter.getLocalPlaylistFragments()
                .stream()
                .forEach(LocalPlaylistFragment::saveImmediate);
    }

    @Override
    public void onTabSelected(final TabLayout.Tab selectedTab) {
        if (DEBUG) {
            Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]");
        }
        updateTitleForTab(selectedTab.getPosition());
    }

    @Override
    public void onTabUnselected(final TabLayout.Tab tab) { }

    @Override
    public void onTabReselected(final TabLayout.Tab tab) {
        if (DEBUG) {
            Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]");
        }
        updateTitleForTab(tab.getPosition());
    }

    public static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;
        /**
         * Keep reference to LocalPlaylistFragments, because their data can be modified by the user
         * during runtime and changes are not committed immediately. However, in some cases,
         * the changes need to be committed immediately by calling
         * {@link LocalPlaylistFragment#saveImmediate()}.
         * The fragments are removed when {@link LocalPlaylistFragment#onDestroy()} is called.
         */
        private final List<LocalPlaylistFragment> localPlaylistFragments = new ArrayList<>();

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.internalTabsList = new ArrayList<>(tabsList);
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(position);

            final Fragment fragment;
            try {
                fragment = tab.getFragment(context);
            } catch (final Throwable t) {
                return new BlankFragment(new ErrorInfo(t, UserAction.GETTING_MAIN_SCREEN_TAB,
                        "Tab " + tab.getClass().getSimpleName() + ":" + tab.getTabName(context)));
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            if (fragment instanceof LocalPlaylistFragment) {
                localPlaylistFragments.add((LocalPlaylistFragment) fragment);
            }

            return fragment;
        }

        public List<LocalPlaylistFragment> getLocalPlaylistFragments() {
            return localPlaylistFragments;
        }

        @Override
        public int getItemPosition(@NonNull final Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return internalTabsList.size();
        }

        public boolean sameTabs(final List<Tab> tabsToCompare) {
            return internalTabsList.equals(tabsToCompare);
        }
    }
}
