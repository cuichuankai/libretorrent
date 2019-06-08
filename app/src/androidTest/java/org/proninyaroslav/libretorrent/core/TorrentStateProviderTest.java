package org.proninyaroslav.libretorrent.core;

import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libtorrent4j.Priority;
import org.proninyaroslav.libretorrent.AbstractTest;
import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TorrentStateProviderTest extends AbstractTest
{
    @SuppressWarnings("unused")
    private static final String TAG = TorrentStateProviderTest.class.getSimpleName();

    private Torrent torrent;
    private String torrentUrl = "http://www.pcds.fi/downloads/applications/internet/browsers/midori/current/debian-ubuntu/midori_0.5.11-0_amd64_.deb.torrent";
    private String torrentName = "midori_0.5.11-0_amd64_.deb";
    private String torrentHash = "3fe5f1a11c51cd01fd09a79621e074dda8eb36b6";
    private Uri dir;

    @Before
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + FileUtils.getDefaultDownloadPath());
        torrent = new Torrent(torrentHash,
                "",
                dir,
                torrentName,
                Collections.singletonList(Priority.DEFAULT),
                System.currentTimeMillis());
    }

    @Test
    public void observeStateTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observeState(torrent.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((state) -> {
                    Log.d(TAG, "state=" + state);
                    assertEquals(torrent.id, state.torrentId);
                    if (state.stateCode == TorrentStateCode.FINISHED ||
                        state.stateCode == TorrentStateCode.SEEDING) {
                        c.countDown();
                        assertEquals(100, state.progress);
                    }
                });

        try {
            engine.addTorrentSync(torrent, downloadTorrent(torrentUrl), false, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(torrent.id), true);
        }
    }

    @Test
    public void observeAdvancedStateTest()
    {
        CountDownLatch c = new CountDownLatch(3);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observeAdvancedState(torrent.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((state) -> {
                    Log.d(TAG, "state=" + state);
                    assertEquals(torrent.id, state.torrentId);
                    c.countDown();
                });

        try {
            engine.addTorrentSync(torrent, downloadTorrent(torrentUrl), false, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(torrent.id), true);
        }
    }

    @Test
    public void observeTrackersStateTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observeTrackersState(torrent.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((stateList) -> {
                    if (!stateList.isEmpty()) {
                        c.countDown();
                        for (TrackerStateParcel state : stateList) {
                            Log.d(TAG, "state=" + state);
                            assertNotNull(state);
                            assertNotNull(state.url);
                        }
                    }
                });

        try {
            engine.addTorrentSync(torrent, downloadTorrent(torrentUrl), false, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(torrent.id), true);
        }
    }

    @Test
    public void observePeersStateTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observePeersState(torrent.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((stateList) -> {
                    if (!stateList.isEmpty()) {
                        c.countDown();
                        for (PeerStateParcel state : stateList) {
                            Log.d(TAG, "state=" + state);
                            assertNotNull(state);
                            assertNotNull(state.ip);
                        }
                    }
                });

        try {
            engine.addTorrentSync(torrent, downloadTorrent(torrentUrl), false, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(torrent.id), true);
        }
    }

    @Test
    public void observePiecesStateTest()
    {
        CountDownLatch c = new CountDownLatch(1);

        assertTrue(engine.isRunning());

        Disposable d = stateProvider.observePiecesState(torrent.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((pieces) -> {
                    c.countDown();
                    assertNotEquals(0, pieces);
                    boolean[] expectedPieces = engine.getPieces(torrent.id);
                    assertEquals(expectedPieces.length, pieces.length);
                });

        try {
            engine.addTorrentSync(torrent, downloadTorrent(torrentUrl), false, true);
            c.await();

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            d.dispose();
            engine.deleteTorrents(Collections.singletonList(torrent.id), true);
        }
    }

    private String downloadTorrent(String url)
    {
        File tmp = FileUtils.makeTempFile(context, ".torrent");
        try {
            byte[] response = Utils.fetchHttpUrl(context, url);
            org.apache.commons.io.FileUtils.writeByteArrayToFile(tmp, response);

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        return "file://" + tmp.getAbsolutePath();
    }
}