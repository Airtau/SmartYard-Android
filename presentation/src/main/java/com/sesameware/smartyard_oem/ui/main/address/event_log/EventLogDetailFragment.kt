@file:Suppress("DEPRECATION")

package com.sesameware.smartyard_oem.ui.main.address.event_log

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.annotation.Px
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import com.google.android.exoplayer2.ui.PlayerView
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.sesameware.data.DataModule
import com.sesameware.domain.model.response.MediaServerType
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import com.sesameware.domain.model.response.Plog
import com.sesameware.smartyard_oem.R
import com.sesameware.smartyard_oem.ui.main.MainActivity
import com.sesameware.smartyard_oem.ui.main.address.event_log.adapters.EventLogDetailAdapter
import timber.log.Timber
import com.sesameware.smartyard_oem.databinding.FragmentEventLogDetailBinding
import com.sesameware.smartyard_oem.ui.main.address.cctv_video.BaseCCTVPlayer
import com.sesameware.smartyard_oem.ui.main.address.cctv_video.DefaultCCTVPlayer
import com.sesameware.smartyard_oem.ui.main.address.cctv_video.ForpostPlayer
import com.sesameware.smartyard_oem.ui.main.address.cctv_video.MacroscopPlayer
import com.sesameware.smartyard_oem.ui.main.address.event_log.adapters.EventLogDetailItemAction
import com.sesameware.smartyard_oem.ui.main.address.event_log.adapters.EventLogDetailVH
import com.sesameware.smartyard_oem.ui.main.settings.faceSettings.dialogAddPhoto.DialogAddPhotoFragment
import com.sesameware.smartyard_oem.ui.main.settings.faceSettings.dialogRemovePhoto.DialogRemovePhotoFragment
import com.sesameware.smartyard_oem.ui.toast
import com.sesameware.smartyard_oem.ui.webview_dialog.WebViewDialogFragment
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime

class EventLogDetailFragment : Fragment() {
    private var _binding: FragmentEventLogDetailBinding? = null
    private val binding get() = _binding!!

    private var rvAdapter: EventLogDetailAdapter? = null

    private val mViewModel by sharedViewModel<EventLogViewModel>()
    private var snapHelper: PagerSnapHelper? = null

    private var prevLoadedIndex = -1
    private var mPlayer: BaseCCTVPlayer? = null
//    private var mEventImage: FaceImageView? = null
    private var videoUrl = ""
    private var currentPosition = -1
    private var currentViewHolder: EventLogDetailVH? = null
    private var previousViewHolder: EventLogDetailVH? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        _binding = FragmentEventLogDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecycler()
        binding.ivEventLogDetailBack.setOnClickListener {
            releasePlayer()
            this.findNavController().popBackStack()
        }
    }

    private fun initPlayer(serverType: MediaServerType) {
        mPlayer?.let { player ->
            if ((serverType == MediaServerType.MACROSCOP) xor (player is MacroscopPlayer)
                || (serverType == MediaServerType.FORPOST) xor (player is ForpostPlayer)) {
                releasePlayer()
            }
        }
        if (mPlayer == null) {
            val callbacks = object : BaseCCTVPlayer.Callbacks {
                override fun onPlayerStateReady() {
                    mPlayer?.mute()
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                override fun onPlayerStateEnded() {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                override fun onPlayerStateIdle() {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                override fun onRenderFirstFrame() {
                    currentViewHolder?.setPlayerViewOpacity(true)
                }

//                override fun onVideoSizeChanged(videoSize: VideoSize) {
//                    mPlayerView?.let {
//                        if (videoSize.width > 0 && videoSize.height > 0) {
//                            if (mEventImage?.measuredHeight != null && mEventImage!!.measuredHeight > 0) {
//                                it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mEventImage!!.measuredHeight)
//                            } else {
//                                val k = it.measuredWidth.toFloat() / videoSize.width.toFloat()
//                                it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (videoSize.height.toFloat() * k).toInt())
//                            }
//                        }
//                    }
//                }

                override fun onAudioAvailabilityChanged(isAvailable: Boolean) {
                    if (currentPosition != -1) {
                        currentViewHolder?.setMuteControlVisibility(isAvailable)
                    }
                }
            }

            mPlayer = when (serverType) {
                MediaServerType.MACROSCOP -> MacroscopPlayer(requireContext(), false, callbacks)
                MediaServerType.FORPOST -> ForpostPlayer(requireContext(), false, callbacks)
                else -> DefaultCCTVPlayer(requireContext(), false, callbacks)
            }
            mPlayer?.let { currentViewHolder?.setPlayer(it) }
        }
    }
    
    // released в onPause
//    override fun onStop() {
//        currentViewHolder?.hidePlayerView()
//        super.onStop()
//    }

    fun releasePlayer() {
        currentViewHolder?.setPlayerViewOpacity(false)
        mPlayer?.releasePlayer()
        mPlayer = null
    }

    private fun setMedia(fromDate: LocalDateTime, data: DoorphoneData) {
        initPlayer(data.serverType)
        videoUrl = data.getHlsAt(
            fromDate,
            EventLogViewModel.EVENT_VIDEO_DURATION_SECONDS,
            DataModule.serverTz
        )
        Timber.d("__Q__    serverType = ${data.serverType}    playVideo media $videoUrl    mPlayer = $mPlayer")
        mPlayer?.prepareMedia(
            videoUrl,
            ZonedDateTime.of(fromDate, ZoneId.of(DataModule.serverTz)).toEpochSecond(),
            EventLogViewModel.EVENT_VIDEO_DURATION_SECONDS,
            EventLogViewModel.EVENT_VIDEO_BACK_SECONDS * 1000L,
            true
        )
    }

    private fun playVideo(position: Int) {
        val adapter = rvAdapter ?: return
        val (day, index) = adapter.getPlog(position)
        if (day == null || index == null) return

        adapter.eventsByDays[day]?.get(index)?.let { eventItem ->
            val fromDate = eventItem.date.minusSeconds(EventLogViewModel.EVENT_VIDEO_BACK_SECONDS)
            if (eventItem.entranceId != null) {
                mViewModel.camMapDataByEntrance[eventItem.entranceId]?.let { data ->
                    setMedia(fromDate, data)
                }
            } else {
                mViewModel.camMapData[eventItem.objectId]?.let { data ->
                    setMedia(fromDate, data)
                }
            }
        }
    }

    override fun onPause() {
        releasePlayer()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        if ((activity as? MainActivity)?.binding?.bottomNav?.selectedItemId == R.id.address) {
            playVideo(currentPosition)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            releasePlayer()
        } else {
            playVideo(currentPosition)
        }

        super.onHiddenChanged(hidden)
    }

    private fun onEventLogDetailItemAction(action: EventLogDetailItemAction) {
        when (action) {
            is EventLogDetailItemAction.OnAddRemoveRegistrationClick ->
                onAddRemoveRegistrationClick(
                    action.position,
                    action.plog
                )
            EventLogDetailItemAction.OnHelpClick -> onHelpClick()
            is EventLogDetailItemAction.OnMuteClick -> onMuteClick(action.isMuted)
            EventLogDetailItemAction.OnPlayOrPause -> onPlayOrPause()
            is EventLogDetailItemAction.OnRewind -> onRewind(action.forward)
            is EventLogDetailItemAction.OnShowOrHidePlayerView -> onShowOrHidePlayerView(action.show)
        }
    }

    private fun onAddRemoveRegistrationClick(position: Int, plog: Plog) {
//        mViewModel.eventsByDaysFilter[day]?.get(index)?.let { plog ->
            plog.detailX?.flags?.let { flags ->
                if (flags.contains(Plog.FLAG_CAN_DISLIKE)) {
                    //пользователь дизлайкнул
                    val faceId = plog.detailX?.faceId?.toInt() ?: 0
                    val photoUrl = mViewModel.faceIdToUrl[faceId] ?: plog.preview
                    val dialogRemovePhoto = DialogRemovePhotoFragment(photoUrl ?: "") {
                        mViewModel.dislike(plog.uuid)
                        flags.remove(Plog.FLAG_CAN_DISLIKE)
                        flags.add(Plog.FLAG_CAN_LIKE)
                        if (faceId > 0) {
                            mViewModel.faceIdToUrl.remove(faceId)
                        }

//                        rvAdapter?.eventsByDays?.get(day)?.set(index, plog)
                        Timber.d("__Q__ plog: $plog")
                        rvAdapter?.notifyItemChanged(position)
                    }
                    dialogRemovePhoto.show(requireActivity().supportFragmentManager, "")
                } else {
                    if (flags.contains(Plog.FLAG_CAN_LIKE)) {
                        //пользователь лайкнул
                        val dialogAddPhoto = DialogAddPhotoFragment(
                            plog.preview ?: "",
                            plog.detailX?.face?.left ?: -1,
                            plog.detailX?.face?.top ?: -1,
                            plog.detailX?.face?.width ?: -1,
                            plog.detailX?.face?.height ?: -1,
                            plog.eventType == Plog.EVENT_OPEN_BY_FACE
                        ) {
                            mViewModel.like(plog.uuid)
                            flags.remove(Plog.FLAG_CAN_LIKE)
                            val faceId = plog.detailX?.faceId?.toInt() ?: 0
                            if (faceId > 0) {
                                flags.add(Plog.FLAG_CAN_DISLIKE)
                            }
                            flags.add(Plog.FLAG_LIKED)
//                            rvAdapter?.eventsByDays?.get(day)?.set(index, plog)
                            Timber.d("__Q__ plog: $plog")
                            rvAdapter?.notifyItemChanged(position)
                        }
                        dialogAddPhoto.show(requireActivity().supportFragmentManager, "")
                    }
                }
            }
//        }
    }

    private fun onHelpClick() {
        WebViewDialogFragment(R.string.help_log_details)
            .show(requireActivity().supportFragmentManager, "HelpLogDetails")
    }

    private fun onMuteClick(isMuted: Boolean) {
        if (isMuted) {
            mPlayer?.mute()
        } else {
            mPlayer?.unMute()
        }
    }

    private fun onPlayOrPause() {
        val player = mPlayer
        if (player == null || !player.isReady()) return
        if (player.isPlaying()) {
            player.pause()
            requireContext().toast(R.string.event_log_pause, false)
        } else {
            player.play()
            requireContext().toast(R.string.event_log_playing, false)
        }
    }

    private fun onRewind(forward: Boolean) {
        val player = mPlayer
        if (player == null || !player.isReady() && !player.isEnded()) return

        var currentPosition = player.currentPosition()
        val endPosition = (player.mediaDuration()) - 1
        var seekStep = EventLogViewModel.SEEK_STEP
        if (!forward) {
            seekStep = -seekStep
        }
        currentPosition += seekStep
        if (currentPosition < 0) {
            currentPosition = 0
        } else if (currentPosition > endPosition) {
            currentPosition = endPosition
        }
        player.seekTo(currentPosition)
    }

    private fun onShowOrHidePlayerView(show: Boolean) {
        val player = mPlayer
        if (player == null || !player.isReady()) return

        if (show) {
            player.play()
            requireContext().toast(R.string.event_log_playing)
        } else {
            player.pause()
            requireContext().toast(R.string.event_log_screenshot)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "NotifyDataSetChanged")
    private fun initRecycler() {
        binding.rvEventLogDetail.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            val spacing = resources.getDimensionPixelSize(R.dimen.event_log_detail_spacing)
            addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
            addItemDecoration(BoundsOffsetDecoration())
            rvAdapter = EventLogDetailAdapter(listOf(), hashMapOf(), ::onEventLogDetailItemAction)
            adapter = rvAdapter
        }

        snapHelper = PagerSnapHelper()
        val onSnapPositionChangeListener = object : OnSnapPositionChangeListener {
            override fun onSnapPositionChanged(prevPosition: Int, newPosition: Int) {
                //Timber.d("__Q__ snap position changed: prev = $prevPosition;  new = $newPosition")

                if (prevPosition != RecyclerView.NO_POSITION) {
                    previousViewHolder = currentViewHolder
                    previousViewHolder?.setPlayerViewOpacity(false)
                    mPlayer?.stop()
                }
                currentViewHolder = binding.rvEventLogDetail.findViewHolderForAdapterPosition(newPosition) as? EventLogDetailVH
                (mPlayer as? DefaultCCTVPlayer)?.getPlayer()?.let {
                    PlayerView.switchTargetView(
                        it,
                        previousViewHolder?.getPlayerView(),
                        currentViewHolder?.getPlayerView()
                    )
                }
                playVideo(newPosition)
                currentPosition = newPosition
            }
        }
        binding.rvEventLogDetail.attachSnapHelperWithListener(
            snapHelper!!,
            SnapOnScrollListener.ScrollBehavior.NOTIFY_ON_SCROLL_IDLE,
            onSnapPositionChangeListener
        )

        binding.rvEventLogDetail.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val llm = binding.rvEventLogDetail.layoutManager as LinearLayoutManager
                val itemCount = binding.rvEventLogDetail.adapter?.itemCount ?: 0
                val itemPosition = llm.findLastCompletelyVisibleItemPosition()
                if (dx > 0 && itemPosition  == itemCount - 1
                    && (mViewModel.lastLoadedDayFilterIndex.value ?: 0) < mViewModel.eventDaysFilter.size - 1) {
                    //Timber.d("__Q__ call getMoreEvents()")
                    mViewModel.loadMoreEvents()
                }
                if (itemPosition == llm.findFirstCompletelyVisibleItemPosition()
                    && itemPosition != RecyclerView.NO_POSITION) {
                    mViewModel.currentEventDayFilter = (binding.rvEventLogDetail.adapter as EventLogDetailAdapter).getPlog(itemPosition).first
                }

                if (dx != 0) {
                    mPlayer?.pause()
                }
            }
        })

        mViewModel.lastLoadedDayFilterIndex.observe(viewLifecycleOwner) { lastLoadedIndex ->
            if (prevLoadedIndex >= lastLoadedIndex) {
                prevLoadedIndex = lastLoadedIndex
                return@observe
            }

            if (mViewModel.eventDaysFilter.isNotEmpty()) {
                rvAdapter?.eventsDay =
                    mViewModel.eventDaysFilter.map { it.day }.subList(0, lastLoadedIndex + 1)
                rvAdapter?.eventsByDays = mViewModel.eventsByDaysFilter
                if (prevLoadedIndex < 0) {
                    rvAdapter?.notifyDataSetChanged()
                    mViewModel.currentEventItem?.let { currentItem ->
                        val p =
                            mViewModel.getEventItemCountTillDay(currentItem.first.plusDays(1)) + currentItem.second
                        (binding.rvEventLogDetail.layoutManager as? LinearLayoutManager)?.let { layoutManager ->
                            layoutManager.scrollToPosition(p)
                            binding.rvEventLogDetail.doOnPreDraw {
                                val targetView =
                                    layoutManager.findViewByPosition(p) ?: return@doOnPreDraw
                                val distanceToFinalSnap = snapHelper?.calculateDistanceToFinalSnap(
                                    layoutManager,
                                    targetView
                                ) ?: return@doOnPreDraw
                                if (p > 0) {
                                    layoutManager.scrollToPositionWithOffset(
                                        p,
                                        -distanceToFinalSnap[0]
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val day1 = mViewModel.eventDaysFilter[prevLoadedIndex].day
                    val count1 = mViewModel.getEventItemCountTillDay(day1)
                    val day2 = mViewModel.eventDaysFilter[lastLoadedIndex].day
                    val count2 = mViewModel.getEventItemCountTillDay(day2)
                    rvAdapter?.notifyItemRangeChanged(count1, count2 - count1)
                }
            }

            prevLoadedIndex = lastLoadedIndex
        }

        mViewModel.progress.observe(viewLifecycleOwner) {
            binding.pbEventLogDetail.isVisible = it
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        currentViewHolder = null
        previousViewHolder = null
        rvAdapter = null
    }
}

/** Works best with a [LinearLayoutManager] in [LinearLayoutManager.HORIZONTAL] orientation */
class LinearHorizontalSpacingDecoration(@Px private val innerSpacing: Int) :
    RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)

        val itemPosition = parent.getChildAdapterPosition(view)

        outRect.left = if (itemPosition == 0) 0 else innerSpacing / 2
        outRect.right = if (itemPosition == state.itemCount - 1) 0 else innerSpacing / 2
    }
}

/** Offset the first and last items to center them */
class BoundsOffsetDecoration : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)

        val itemPosition = parent.getChildAdapterPosition(view)

        // It is crucial to refer to layoutParams.width (view.width is 0 at this time)!
        val itemWidth = view.layoutParams.width
        val offset = (parent.measuredWidthAndState - itemWidth) / 2

        if (itemPosition == 0) {
            outRect.left = offset
        } else if (itemPosition == state.itemCount - 1) {
            outRect.right = offset
        }
    }
}
