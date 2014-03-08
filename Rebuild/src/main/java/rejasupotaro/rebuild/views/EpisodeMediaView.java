package rejasupotaro.rebuild.views;

import com.squareup.otto.Subscribe;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import rejasupotaro.rebuild.R;
import rejasupotaro.rebuild.events.BusProvider;
import rejasupotaro.rebuild.events.DownloadEpisodeCompleteEvent;
import rejasupotaro.rebuild.events.ReceivePauseActionEvent;
import rejasupotaro.rebuild.events.ReceiveResumeActionEvent;
import rejasupotaro.rebuild.listener.LoadListener;
import rejasupotaro.rebuild.listener.OnPlayerSeekListener;
import rejasupotaro.rebuild.media.PodcastPlayer;
import rejasupotaro.rebuild.models.Episode;
import rejasupotaro.rebuild.notifications.PodcastPlayerNotification;
import rejasupotaro.rebuild.services.EpisodeDownloadService;
import rejasupotaro.rebuild.tools.OnContextExecutor;
import rejasupotaro.rebuild.utils.DateUtils;
import rejasupotaro.rebuild.utils.ToastUtils;
import rejasupotaro.rebuild.utils.UiAnimations;

public class EpisodeMediaView extends LinearLayout {

    private LoadListener loadListener;

    private OnContextExecutor onContextExecutor = new OnContextExecutor();

    private TextView episodeTitleTextView;

    private View mediaStartButtonOnImageCover;

    private TextView mediaCurrentTimeTextView;

    private TextView mediaDurationTextView;

    private CheckBox mediaPlayAndPauseButton;

    private SeekBar seekBar;

    private FontAwesomeTextView episodeDownloadButton;

    public EpisodeMediaView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setup(Episode episode, LoadListener loadListener) {
        BusProvider.getInstance().register(this);
        View view = inflate(getContext(), R.layout.header_episode_detail, null);
        findViews(view);
        addView(view);
        setEpisode(episode);
        this.loadListener = loadListener;
    }

    private void findViews(View view) {
        episodeTitleTextView = (TextView) view.findViewById(R.id.episode_title);
        mediaStartButtonOnImageCover = view.findViewById(R.id.episode_detail_header_cover);
        mediaCurrentTimeTextView = (TextView) view.findViewById(R.id.media_current_time);
        mediaDurationTextView = (TextView) view.findViewById(R.id.media_duration);
        mediaPlayAndPauseButton = (CheckBox) view.findViewById(R.id.media_play_and_pause_button);
        seekBar = (SeekBar) view.findViewById(R.id.media_seekbar);
        episodeDownloadButton = (FontAwesomeTextView) view
                .findViewById(R.id.episode_download_button);
    }

    public void setEpisode(Episode episode) {
        String originalTitle = episode.getTitle();
        int startIndex = originalTitle.indexOf(':');
        episodeTitleTextView.setText(originalTitle.substring(startIndex + 2));

        setupMediaPlayAndPauseButton(episode);
        setupDownloadButton(episode);
        setupSeekBar(episode);
    }

    public void onDestroy() {
        BusProvider.getInstance().unregister(this);
    }

    private void setupMediaPlayAndPauseButton(final Episode episode) {
        PodcastPlayer podcastPlayer = PodcastPlayer.getInstance();
        if (podcastPlayer.isPlayingEpisode(episode) && podcastPlayer.isPlaying()) {
            mediaPlayAndPauseButton.setChecked(true);
            mediaStartButtonOnImageCover.setVisibility(View.GONE);
        } else {
            mediaPlayAndPauseButton.setChecked(false);
            if (podcastPlayer.isPlaying()) {
                mediaStartButtonOnImageCover.setVisibility(View.VISIBLE);
                mediaStartButtonOnImageCover.setAlpha(1);
            }
        }

        mediaPlayAndPauseButton.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            start(episode);
                        } else {
                            pause(episode);
                        }
                    }
                });
    }

    private void start(final Episode episode) {
        final PodcastPlayer podcastPlayer = PodcastPlayer.getInstance();
        if (shouldRestart(episode)) {
            podcastPlayer.start();
            seekBar.setEnabled(true);
            PodcastPlayerNotification.notify(getContext(), episode);
        } else {
            loadListener.showProgress();
            mediaPlayAndPauseButton.setEnabled(false);
            podcastPlayer.start(getContext(), episode, new PodcastPlayer.StateChangedListener() {
                @Override
                public void onStart() {
                    if (getContext() == null) {
                        pause(episode);
                    } else {
                        loadListener.showContent();
                        UiAnimations.fadeOut(mediaStartButtonOnImageCover, 300, 1000);

                        seekBar.setEnabled(true);
                        mediaPlayAndPauseButton.setEnabled(true);
                        PodcastPlayerNotification.notify(getContext(), episode);
                    }
                }
            });
        }
    }

    private boolean shouldRestart(Episode episode) {
        PodcastPlayer podcastPlayer = PodcastPlayer.getInstance();
        return (podcastPlayer.isPlayingEpisode(episode)
                && podcastPlayer.getCurrentPosition() > 0);
    }

    private void pause(final Episode episode) {
        final PodcastPlayer podcastPlayer = PodcastPlayer.getInstance();
        podcastPlayer.pause();
        seekBar.setEnabled(false);
        PodcastPlayerNotification
                .notify(getContext(), episode, PodcastPlayer.getInstance().getCurrentPosition());
    }

    private void setupDownloadButton(final Episode episode) {
        episodeDownloadButton.setEnabled(true);
        if (episode.isDownloaded()) {
            episodeDownloadButton.setText(getContext().getString(R.string.clear_cache));
            episodeDownloadButton.prepend(FontAwesomeTextView.Icon.MINUS);
            episodeDownloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    episode.clearCache();
                    setupDownloadButton(episode);
                }
            });
        } else if (EpisodeDownloadService.isDownloading(episode)) {
            episodeDownloadButton.setEnabled(false);
            episodeDownloadButton.setText(getContext().getString(R.string.downloading));
            episodeDownloadButton.prepend(FontAwesomeTextView.Icon.SPINNER);
        } else {
            episodeDownloadButton.setText(getContext().getString(R.string.download));
            episodeDownloadButton.prepend(FontAwesomeTextView.Icon.DOWNLOAD);
            episodeDownloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EpisodeDownloadService.startDownload(getContext(), episode);
                    episodeDownloadButton.setEnabled(false);
                    episodeDownloadButton.setText(getContext().getString(R.string.downloading));
                    episodeDownloadButton.prepend(FontAwesomeTextView.Icon.SPINNER);
                }
            });
        }
    }

    private void setupSeekBar(final Episode episode) {
        mediaDurationTextView.setText(episode.getDuration());

        if (PodcastPlayer.getInstance().isPlaying()) {
            updateCurrentTime(PodcastPlayer.getInstance().getCurrentPosition());
        } else {
            updateCurrentTime(0);
        }

        seekBar.setOnSeekBarChangeListener(new OnPlayerSeekListener());
        seekBar.setMax(DateUtils.durationToInt(episode.getDuration()));
        if (PodcastPlayer.getInstance().isPlayingEpisode(episode)) {
            seekBar.setEnabled(true);
        } else {
            seekBar.setEnabled(false);
        }

        PodcastPlayer.getInstance().setCurrentTimeListener(
                new PodcastPlayer.CurrentTimeListener() {
                    @Override
                    public void onTick(int currentPosition) {
                        if (PodcastPlayer.getInstance().isPlayingEpisode(episode)) {
                            updateCurrentTime(currentPosition);
                            PodcastPlayerNotification
                                    .notify(getContext(), episode, currentPosition);
                        } else {
                            updateCurrentTime(0);
                        }
                    }
                });
    }

    private void updateCurrentTime(int currentPosition) {
        mediaCurrentTimeTextView.setText(DateUtils.formatCurrentTime(currentPosition));
        seekBar.setProgress(currentPosition);
    }

    @Subscribe
    public void onEpisodeDownloadComplete(final DownloadEpisodeCompleteEvent event) {
        onContextExecutor.execute(getContext(), new Runnable() {
            @Override
            public void run() {
                Episode episode = event.getEpisode();
                String message = getContext().getString(
                        R.string.episode_download_completed,
                        episode.getTitle());
                ToastUtils.show(getContext(), message);
                setupDownloadButton(episode);
            }
        });
    }

    @Subscribe
    public void onReceivePauseAction(ReceivePauseActionEvent event) {
        onContextExecutor.execute(getContext(), new Runnable() {
            @Override
            public void run() {
                mediaPlayAndPauseButton.setChecked(false);
            }
        });
    }

    @Subscribe
    public void onReceivePauseAction(ReceiveResumeActionEvent event) {
        onContextExecutor.execute(getContext(), new Runnable() {
            @Override
            public void run() {
                mediaPlayAndPauseButton.setChecked(true);
            }
        });
    }
}
