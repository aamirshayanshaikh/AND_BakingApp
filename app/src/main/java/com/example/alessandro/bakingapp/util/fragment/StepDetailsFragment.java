package com.example.alessandro.bakingapp.util.fragment;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.example.alessandro.bakingapp.R;
import com.example.alessandro.bakingapp.activity.RecipeDetailsActivity;
import com.example.alessandro.bakingapp.activity.StepDetailsActivity;
import com.example.alessandro.bakingapp.data.Step;
import com.example.alessandro.bakingapp.util.media.MediaSessionCallback;
import com.example.alessandro.bakingapp.util.ImageUtils;
import com.example.alessandro.bakingapp.util.NetworkUtils;
import com.squareup.picasso.Picasso;

import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

public class StepDetailsFragment extends Fragment implements Player.EventListener {

    private static final String TAG = StepDetailsFragment.class.getSimpleName();
    private static final String POSITION_MILLISECONDS = "position_milliseconds";
    private static final String PLAY_WHEN_READY = "state_playing";
    private static MediaSessionCompat mediaSession;

    @BindView(R.id.nested_scroll_view_step)
    NestedScrollView nestedScrollViewStep;

    @BindView(R.id.player_view_step)
    PlayerView playerView;

    @BindView(R.id.image_view_step)
    ImageView imageViewStep;

    @BindView(R.id.text_view_step_description)
    TextView textViewStepDescription;

    @BindView(R.id.button_previous_step)
    Button buttonPreviousStep;

    @BindView(R.id.button_next_step)
    Button buttonNextStep;

    private StepDetailsOnClickListener listener;
    private SimpleExoPlayer exoPlayer;
    private PlaybackStateCompat.Builder stateBuilder;
    private String videoUrl;
    private long playbackPosition;
    private boolean playWhenReady = true;
    private boolean playerStopped = false;
    private long playerStopPosition;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the fragment
     */
    public StepDetailsFragment() {

    }

    public static StepDetailsFragment newInstance(Bundle bundle) {
        StepDetailsFragment stepDetailsFragment = new StepDetailsFragment();
        stepDetailsFragment.setArguments(bundle);
        return stepDetailsFragment;
    }

    /**
     * Inflates the fragment layout file and sets the correct resource for the image to display
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_details, container, false);
        ButterKnife.bind(this, view);

        Context context = getContext();
        Bundle bundle = getArguments();
        List<Step> steps = null;
        int position = -1;

        if (bundle != null) {
            steps = bundle.getParcelableArrayList(getString(R.string.steps));
            position = bundle.getInt(getString(R.string.step_position));
        } else {
            Toast.makeText(context, R.string.media_url_not_found, Toast.LENGTH_SHORT).show();
        }

        if (steps != null) {
            if (position < 0 || position > steps.size()) {
                Toast.makeText(context, R.string.step_could_not_be_loaded, Toast.LENGTH_SHORT).show();
                return view;
            }
            // Get current Step
            Step currentStep = steps.get(position);

            if (currentStep == null) {
                Toast.makeText(context, R.string.step_could_not_be_loaded, Toast.LENGTH_SHORT).show();
                return view;
            }

            textViewStepDescription.setText(currentStep.getDescription());
            //Previous and Next Step button configuration
            setUpStepButton(buttonPreviousStep, steps, position, -1);
            setUpStepButton(buttonNextStep, steps, position, 1);

            AppCompatActivity activity = ((AppCompatActivity) getActivity());
            ActionBar actionBar = null;
            if (activity != null) {
                actionBar = activity.getSupportActionBar();
            }

            // Get current position video url
            videoUrl = currentStep.getVideoUrl();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Check video url and network connection
                if (!TextUtils.isEmpty(videoUrl) && NetworkUtils.isOnline(Objects.requireNonNull(context))) {
                    initializeMediaSession(context);

                    // Check device orientation configuration
                    Configuration configuration = getResources().getConfiguration();
                    if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            && !getResources().getBoolean(R.bool.isTablet)) {
                        if (actionBar != null) {
                            actionBar.hide();
                        }

                        if (activity != null) {
                            activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        }

                        // Set views visibility
                        nestedScrollViewStep.setVisibility(View.GONE);
                        buttonPreviousStep.setVisibility(View.GONE);
                        buttonNextStep.setVisibility(View.GONE);
                    } else {
                        imageViewStep.setVisibility(View.GONE);
                        RelativeLayout.LayoutParams layoutParams =
                                (RelativeLayout.LayoutParams) textViewStepDescription.getLayoutParams();
                        layoutParams.addRule(RelativeLayout.BELOW, R.id.player_view_step);
                        textViewStepDescription.setLayoutParams(layoutParams);
                    }
                } else {
                    // hide video player
                    playerView.setVisibility(View.GONE);
                    String recipeName = null;
                    if (actionBar != null) {
                        if (actionBar.getTitle() != null) {
                            recipeName = actionBar.getTitle().toString();
                        }
                    }
                    // Set an Image instead of video Player
                    String imageUrl = currentStep.getThumbnailUrl();
                    int placeholderId = ImageUtils.getImageResourceId(Objects.requireNonNull(context), Objects.requireNonNull(recipeName));
                    if (!TextUtils.isEmpty(imageUrl)) {
                        Picasso.with(context)
                                .load(imageUrl)
                                .placeholder(placeholderId)
                                .into(imageViewStep);
                    } else {
                        Picasso.with(context)
                                .load(placeholderId)
                                .into(imageViewStep);
                    }

                    RelativeLayout.LayoutParams layoutParams =
                            (RelativeLayout.LayoutParams) textViewStepDescription.getLayoutParams();
                    layoutParams.addRule(RelativeLayout.BELOW, R.id.image_view_step);
                    textViewStepDescription.setLayoutParams(layoutParams);
                }
            }

            if (savedInstanceState != null) {
                playbackPosition = savedInstanceState.getLong(POSITION_MILLISECONDS);
                playWhenReady = savedInstanceState.getBoolean(PLAY_WHEN_READY);
            }
        }

        return view;
    }

    /**
     * Set Previous and Next Buttons using offset
     */
    private void setUpStepButton(Button button, final List<Step> steps, final int position, final int offset) {
        int newPosition = position + offset;
        Step step = null;
        if (newPosition > -1 && newPosition < steps.size()) {
            step = steps.get(newPosition);

        }
        if (step != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onStepSelected(position + offset);
                    if (textViewStepDescription != null) {
                        textViewStepDescription.setText(null);
                    }

                }
            });
        } else {
            button.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof StepDetailsOnClickListener) {
            listener = (StepDetailsOnClickListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + getString(R.string.must_implement)
                    + StepDetailsOnClickListener.class.getSimpleName());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (exoPlayer != null) {
            outState.putLong(POSITION_MILLISECONDS, exoPlayer.getCurrentPosition());
            outState.putBoolean(PLAY_WHEN_READY, playWhenReady);
        }

        outState.putIntArray(getString(R.string.scroll_position),
                new int[]{nestedScrollViewStep.getScrollX(), nestedScrollViewStep.getScrollY()});
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            final int[] position = savedInstanceState.getIntArray(getString(R.string.scroll_position));
            if (position != null) {
                nestedScrollViewStep.post(new Runnable() {
                    public void run() {
                        nestedScrollViewStep.scrollTo(position[0], position[1]);
                    }
                });
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!TextUtils.isEmpty(videoUrl) && NetworkUtils.isOnline(Objects.requireNonNull(context))) {
                initializePlayer(Uri.parse(videoUrl), context);
                exoPlayer.seekTo(playbackPosition);
                exoPlayer.setPlayWhenReady(playWhenReady);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (exoPlayer == null) {
            return;
        }
        playbackPosition = exoPlayer.getCurrentPosition();
        playWhenReady = exoPlayer.getPlayWhenReady();
        exoPlayer.setPlayWhenReady(false);
        releasePlayer();
    }

    @Override
    public void onStop() {
        super.onStop();
        if(exoPlayer != null) {
            playerStopPosition = exoPlayer.getCurrentPosition();
            playerStopped = true;
            releasePlayer();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mediaSession != null) {
            mediaSession.setActive(false);
        }
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    //Initialize Media Session
    private void initializeMediaSession(Context context) {
        mediaSession = new MediaSessionCompat(context, TAG);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setMediaButtonReceiver(null);
        stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                );

        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setCallback(new MediaSessionCallback(exoPlayer));
        mediaSession.setActive(true);

    }

    // Initialize Player
    private void initializePlayer(Uri uri, Context context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayerFactory.newSimpleInstance(
                    new DefaultRenderersFactory(context),
                    new DefaultTrackSelector(), new DefaultLoadControl());

            // Set player
            playerView.setPlayer(exoPlayer);
            exoPlayer.addListener(this);

            exoPlayer.setPlayWhenReady(true);
            MediaSource mediaSource = buildMediaSource(uri);
            exoPlayer.prepare(mediaSource, true, false);

            if (playbackPosition != 0 && !playerStopped){
                exoPlayer.seekTo(playbackPosition);
            } else {
                exoPlayer.seekTo(playerStopPosition);
            }
        }
    }

    // Create media source
    private MediaSource buildMediaSource(Uri uri) {
        return new ExtractorMediaSource.Factory(
                new DefaultHttpDataSourceFactory(getString(R.string.app_name))).
                createMediaSource(uri);

    }


    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == Player.STATE_READY && playWhenReady) {
            stateBuilder.setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    exoPlayer.getCurrentPosition(),
                    1f
            );

            // If device is a Tablet
            if (getResources().getBoolean(R.bool.isTablet)) {
                RecipeDetailsActivity recipeDetailsActivity = (RecipeDetailsActivity) getActivity();
                if (recipeDetailsActivity != null) {
                    recipeDetailsActivity.setIdleState(true);
                }
            } else {
                //If device is a Smartphone
                StepDetailsActivity stepDetailsActivity = (StepDetailsActivity) getActivity();
                if (stepDetailsActivity != null) {
                    stepDetailsActivity.setIdleState(true);
                }
            }
        } else if (playbackState == Player.STATE_READY) {
            stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    exoPlayer.getCurrentPosition(),
                    1f
            );
        }
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    @Override
    public void onPositionDiscontinuity(int reason) {

    }


    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onSeekProcessed() {

    }


    public interface StepDetailsOnClickListener {
        void onStepSelected(int position);
    }
}
