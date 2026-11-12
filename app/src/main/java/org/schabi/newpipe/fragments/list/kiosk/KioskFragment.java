/*
 * SPDX-FileCopyrightText: 2017-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.services.media_ccc.extractors.MediaCCCLiveStreamKiosk;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.Localization;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

public class KioskFragment extends BaseListInfoFragment<StreamInfoItem, KioskInfo> {
    private static final String FOR_YOU_TITLE = "For You";
    private static final int HISTORY_SAMPLE_SIZE = 10;

    @State
    String kioskId = "";
    String kioskTranslatedName;
    @State
    ContentCountry contentCountry;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    public static KioskFragment getInstance(final int serviceId) throws ExtractionException {
        return getInstance(serviceId, NewPipe.getService(serviceId)
                .getKioskList().getDefaultKioskId());
    }

    public static KioskFragment getInstance(final int serviceId, final String kioskId)
            throws ExtractionException {
        final KioskFragment instance = new KioskFragment();
        final StreamingService service = NewPipe.getService(serviceId);
        final ListLinkHandlerFactory kioskLinkHandlerFactory = service.getKioskList()
                .getListLinkHandlerFactoryByType(kioskId);
        instance.setInitialData(serviceId,
                kioskLinkHandlerFactory.fromId(kioskId).getUrl(), kioskId);
        instance.kioskId = kioskId;
        return instance;
    }

    public KioskFragment() {
        super(UserAction.REQUESTED_KIOSK);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        kioskTranslatedName = isYouTube()
                ? FOR_YOU_TITLE
                : KioskTranslator.getTranslatedKioskName(kioskId, activity);
        name = kioskTranslatedName;
        contentCountry = Localization.getPreferredContentCountry(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!Localization.getPreferredContentCountry(requireContext()).equals(contentCountry)) {
            reloadContent();
        }
        if (useAsFrontPage && activity != null) {
            try {
                setTitle(kioskTranslatedName);
            } catch (final Exception e) {
                showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR, "Setting kiosk title"));
            }
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null && useAsFrontPage) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    private boolean isYouTube() {
        return serviceId == ServiceList.YouTube.getServiceId();
    }

    @Override
    public Single<KioskInfo> loadResult(final boolean forceReload) {
        contentCountry = Localization.getPreferredContentCountry(requireContext());
        if (!isYouTube()) {
            return ExtractorHelper.getKioskInfo(serviceId, url, forceReload);
        }
        return loadForYou();
    }

    /**
     * "For You" feed: related videos of the last watched videos,
     * without already watched ones and duplicates.
     */
    private Single<KioskInfo> loadForYou() {
        return new HistoryRecordManager(requireContext()).getStreamHistorySortedById()
                .firstOrError()
                .flatMap(history -> {
                    final Set<String> watched = new HashSet<>();
                    for (final StreamHistoryEntry e : history) {
                        watched.add(e.getStreamEntity().getUrl());
                    }

                    final List<StreamHistoryEntry> sorted = new ArrayList<>(history);
                    Collections.sort(sorted,
                            (a, b) -> b.getAccessDate().compareTo(a.getAccessDate()));
                    final List<StreamHistoryEntry> last = new ArrayList<>(
                            sorted.subList(0, Math.min(HISTORY_SAMPLE_SIZE, sorted.size())));

                    return Observable.fromIterable(last)
                            .flatMapMaybe(e -> ExtractorHelper
                                    .getStreamInfo(serviceId,
                                            e.getStreamEntity().getUrl(), false)
                                    .map(StreamInfo::getRelatedItems)
                                    .toMaybe()
                                    .onErrorComplete())
                            .toList()
                            .map(lists -> {
                                final Set<String> seen = new HashSet<>();
                                final List<StreamInfoItem> items = new ArrayList<>();
                                for (final List<InfoItem> l : lists) {
                                    for (final InfoItem i : l) {
                                        if (i instanceof StreamInfoItem
                                                && !watched.contains(i.getUrl())
                                                && seen.add(i.getUrl())) {
                                            items.add((StreamInfoItem) i);
                                        }
                                    }
                                }
                                Collections.shuffle(items);

                                final Constructor<KioskInfo> c = KioskInfo.class
                                        .getDeclaredConstructor(int.class,
                                                ListLinkHandler.class, String.class);
                                c.setAccessible(true);
                                final KioskInfo info = c.newInstance(serviceId,
                                        new ListLinkHandler(url, url, kioskId,
                                                Collections.emptyList(), ""),
                                        FOR_YOU_TITLE);
                                info.setRelatedItems(items);
                                return info;
                            });
                });
    }

    @Override
    public Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        if (isYouTube()) {
            // For You feed has a single page
            return Single.just(ListExtractor.InfoItemsPage.emptyPage());
        }
        return ExtractorHelper.getMoreKioskItems(serviceId, url, currentNextPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final KioskInfo result) {
        super.handleResult(result);

        name = kioskTranslatedName;
        setTitle(kioskTranslatedName);
    }

    @Override
    public void showEmptyState() {
        // show "no live streams" for live stream kiosk
        super.showEmptyState();
        if (MediaCCCLiveStreamKiosk.KIOSK_ID.equals(currentInfo.getId())
                && ServiceList.MediaCCC.getServiceId() == currentInfo.getServiceId()) {
            setEmptyStateMessage(R.string.no_live_streams);
        }
    }
                            }
