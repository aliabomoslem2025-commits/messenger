/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.airbnb.mvrx.activityViewModel
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.SpaceStateHandler
import im.vector.app.core.extensions.commitTransaction
import im.vector.app.core.platform.OnBackPressed
import im.vector.app.core.platform.VectorBaseActivity
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.platform.VectorMenuProvider
import im.vector.app.core.resources.BuildMeta
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.ui.views.CurrentCallsView
import im.vector.app.core.ui.views.CurrentCallsViewPresenter
import im.vector.app.core.ui.views.KeysBackupBanner
import im.vector.app.core.ui.views.VerifyDeviceBanner
import im.vector.app.core.utils.openApplicationStore
import im.vector.app.core.utils.openUrlInChromeCustomTab
import im.vector.app.databinding.FragmentNewHomeDetailBinding
import im.vector.app.features.call.SharedKnownCallsViewModel
import im.vector.app.features.call.VectorCallActivity
import im.vector.app.features.call.dialpad.PstnDialActivity
import im.vector.app.features.call.webrtc.WebRtcCallManager
import im.vector.app.features.contactsbook.ContactsBookFragment
import im.vector.app.features.crypto.verification.self.SelfVerificationBottomSheet
import im.vector.app.features.elementx.MigrationBannerAction
import im.vector.app.features.elementx.MigrationBannerState
import im.vector.app.features.elementx.MigrationBannerViewModel
import im.vector.app.features.home.room.list.UnreadCounterBadgeView
import im.vector.app.features.home.room.list.actions.RoomListSharedAction
import im.vector.app.features.home.room.list.actions.RoomListSharedActionViewModel
import im.vector.app.features.home.room.list.home.HomeRoomListAction
import im.vector.app.features.home.room.list.home.HomeRoomListFragment
import im.vector.app.features.home.room.list.home.HomeRoomListViewModel
import im.vector.app.features.home.room.list.home.HomeRoomListViewState
import im.vector.app.features.home.room.list.home.header.HomeRoomFilter
import im.vector.app.features.home.room.list.home.NewChatBottomSheet
import im.vector.app.features.popup.PopupAlertManager
import im.vector.app.features.popup.VerificationVectorAlert
import im.vector.app.features.qrcode.QrCodeScannerActivity
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.settings.VectorSettingsActivity.Companion.EXTRA_DIRECT_ACCESS_SECURITY_PRIVACY_MANAGE_SESSIONS
import im.vector.app.features.spaces.SpaceListBottomSheet
import im.vector.app.features.workers.signout.BannerState
import im.vector.app.features.workers.signout.ServerBackupStatusAction
import im.vector.app.features.workers.signout.ServerBackupStatusViewModel
import im.vector.lib.strings.CommonStrings
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.matrix.android.sdk.api.session.crypto.model.DeviceInfo
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import javax.inject.Inject

@AndroidEntryPoint
class NewHomeDetailFragment :
        VectorBaseFragment<FragmentNewHomeDetailBinding>(),
        KeysBackupBanner.Delegate,
        CurrentCallsView.Callback,
        OnBackPressed,
        VectorMenuProvider,
        VerifyDeviceBanner.Delegate {

    @Inject lateinit var avatarRenderer: AvatarRenderer
    @Inject lateinit var colorProvider: ColorProvider
    @Inject lateinit var alertManager: PopupAlertManager
    @Inject lateinit var callManager: WebRtcCallManager
    @Inject lateinit var vectorPreferences: VectorPreferences
    @Inject lateinit var spaceStateHandler: SpaceStateHandler
    @Inject lateinit var buildMeta: BuildMeta

    private val viewModel: HomeDetailViewModel by fragmentViewModel()
    private val newHomeDetailViewModel: NewHomeDetailViewModel by fragmentViewModel()
    private val homeRoomListViewModel: HomeRoomListViewModel by activityViewModel()

    private var isSyncingTelegramHomeTabs = false
    private var homeSearchTextWatcher: TextWatcher? = null
    private val unknownDeviceDetectorSharedViewModel: UnknownDeviceDetectorSharedViewModel by activityViewModel()
    private val serverBackupStatusViewModel: ServerBackupStatusViewModel by activityViewModel()
    private val migrationBannerViewModel: MigrationBannerViewModel by activityViewModel()

    private lateinit var sharedActionViewModel: HomeSharedActionViewModel
    private lateinit var sharedRoomListActionViewModel: RoomListSharedActionViewModel
    private lateinit var sharedCallActionViewModel: SharedKnownCallsViewModel

    private val newChatBottomSheet = NewChatBottomSheet()
    private val spaceListBottomSheet = SpaceListBottomSheet()

    private var spacesUnreadLabel: String? = null

    private var hasUnreadRooms = false
        set(value) {
            if (value != field) {
                field = value
                invalidateOptionsMenu()
            }
        }

    override fun getMenuRes() = R.menu.room_list

    override fun handleMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_home_mark_all_as_read -> {
                viewModel.handle(HomeDetailAction.MarkAllRoomsRead)
                true
            }
            R.id.menu_home_dialpad -> {
                startActivity(Intent(requireContext(), PstnDialActivity::class.java))
                true
            }
            R.id.menu_home_spaces -> {
                spaceListBottomSheet.takeIf { !it.isAdded }?.show(requireActivity().supportFragmentManager, SpaceListBottomSheet.TAG)
                true
            }
            R.id.menu_home_open_drawer -> {
                sharedActionViewModel.post(HomeActivitySharedAction.OpenDrawer)
                true
            }
            R.id.menu_home_search -> true // The persistent search input handles the query.
            else -> false
        }
    }

    override fun handlePrepareMenu(menu: Menu) {
        withState(viewModel) { state ->
            val isRoomList = state.currentTab is HomeTab.RoomList
            menu.findItem(R.id.menu_home_mark_all_as_read).isVisible = isRoomList && hasUnreadRooms
            menu.findItem(R.id.menu_home_dialpad).isVisible = state.showDialPadTab
            val spacesItem = menu.findItem(R.id.menu_home_spaces)
            spacesItem.isVisible = isRoomList
            val spacesTitle = getString(R.string.a11y_open_spaces)
            spacesItem.title = spacesUnreadLabel?.let { "$spacesTitle ($it)" } ?: spacesTitle

            // Search is a persistent input below the toolbar, not an overflow action.
            menu.findItem(R.id.menu_home_search).isVisible = false
        }
    }

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentNewHomeDetailBinding {
        return FragmentNewHomeDetailBinding.inflate(inflater, container, false)
    }

    private val currentCallsViewPresenter = CurrentCallsViewPresenter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedActionViewModel = activityViewModelProvider.get(HomeSharedActionViewModel::class.java)
        sharedCallActionViewModel = activityViewModelProvider.get(SharedKnownCallsViewModel::class.java)
        setupToolbar()
        setupMigrationBanner()
        setupKeysBackupBanner()
        setupVerificationBanner()
        setupActiveCallView()
        setupDebugButton()
        setupFabs()
        setupObservers()

        setupTelegramHomeSearch()
        setupTelegramBottomNavigation()

        childFragmentManager.commitTransaction {
            add(R.id.roomListContainer, HomeRoomListFragment::class.java, null, HOME_ROOM_LIST_FRAGMENT_TAG)
        }
        setupTelegramHomeTabs()

        viewModel.onEach(HomeDetailViewState::selectedSpace) { selectedSpace ->
            onSpaceChange(selectedSpace)
        }

        viewModel.onEach(HomeDetailViewState::myMatrixItem) { matrixItem ->
            if (matrixItem != null) {
                avatarRenderer.render(matrixItem, views.telegramTabProfileIcon)
            } else {
                views.telegramTabProfileIcon.setImageResource(R.drawable.ic_user_round)
            }
        }

        viewModel.observeViewEvents { viewEvent ->
            when (viewEvent) {
                HomeDetailViewEvents.CallStarted -> Unit
                is HomeDetailViewEvents.FailToCall -> Unit
                HomeDetailViewEvents.Loading -> showLoadingDialog()
            }
        }

        unknownDeviceDetectorSharedViewModel.onEach { state ->
            state.unknownSessions.invoke()?.let { unknownDevices ->
                val uid = PopupAlertManager.REVIEW_LOGIN_UID
                if (unknownDevices.firstOrNull()?.currentSessionTrust == true) {
                    alertManager.cancelAlert(uid)
                    val olderUnverified = unknownDevices.filter { !it.isNew }
                    val newest = unknownDevices.firstOrNull { it.isNew }?.deviceInfo
                    if (newest != null) {
                        promptForNewUnknownDevices(uid, state, newest)
                    } else if (olderUnverified.isNotEmpty()) {
                        // In this case we prompt to go to settings to review logins
                        promptToReviewChanges(uid, state, olderUnverified.map { it.deviceInfo })
                    }
                } else {
                    // cancel as there are not anymore untrusted devices
                    alertManager.cancelAlert(uid)
                }
            }
        }

        sharedCallActionViewModel
                .liveKnownCalls
                .observe(viewLifecycleOwner) {
                    currentCallsViewPresenter.updateCall(callManager.getCurrentCall(), callManager.getCalls())
                    invalidateOptionsMenu()
                }

        newHomeDetailViewModel.onEach { viewState ->
            spacesUnreadLabel = when (val badgeState = viewState.spacesNotificationCounterBadgeState) {
                is UnreadCounterBadgeView.State.Count -> badgeState.count
                        .takeIf { it > 0 }
                        ?.let { count -> if (count > 99) "99+" else count.toString() }
                is UnreadCounterBadgeView.State.Text -> badgeState.text.takeIf { it.isNotBlank() }
            }
            // Spaces is now in the toolbar menu; keep its unread signal there instead of
            // drawing a detached badge where the hidden floating action button used to be.
            invalidateOptionsMenu()
        }
    }

    private fun setupTelegramHomeSearch() {
        val input = views.telegramHomeSearchInput
        homeSearchTextWatcher?.let(input::removeTextChangedListener)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                homeRoomListViewModel.handle(HomeRoomListAction.SearchRooms(s?.toString().orEmpty()))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        homeSearchTextWatcher = watcher
        input.addTextChangedListener(watcher)
    }

    /**
     * Keep the floating four-item navigation tied to real Element/Matrix destinations.
     * Contacts opens the device contacts book; profile opens the signed-in user's
     * Matrix profile. No placeholder tabs or fake screen content are introduced.
     */
    private fun setupTelegramBottomNavigation() {
        val tabs = listOf(
                views.telegramTabChats,
                views.telegramTabContacts,
                views.telegramTabSettings,
                views.telegramTabProfile
        )

        fun selectTab(selected: View) {
            tabs.forEach { tab ->
                tab.isSelected = tab.id == selected.id
            }
        }

        selectTab(views.telegramTabChats)
        withState(viewModel) { state ->
            state.myMatrixItem?.let { avatarRenderer.render(it, views.telegramTabProfileIcon) }
        }
        // Telegram's Chats tab means all joined conversations, not Element's People-only view.
        viewModel.handle(HomeDetailAction.SwitchTab(HomeTab.RoomList(RoomListDisplayMode.ROOMS)))
        homeRoomListViewModel.handle(HomeRoomListAction.ChangeRoomFilter(HomeRoomFilter.ALL))

        views.telegramTabChats.debouncedClicks {
            selectTab(views.telegramTabChats)
            views.toolbar.title = ""
            views.toolbar.title = getString(R.string.telegram_home_title)
            views.telegramHomeSearchInput.text?.clear()
            viewModel.handle(HomeDetailAction.SwitchTab(HomeTab.RoomList(RoomListDisplayMode.ROOMS)))
            homeRoomListViewModel.handle(HomeRoomListAction.ChangeRoomFilter(HomeRoomFilter.ALL))
        }

        views.telegramTabContacts.debouncedClicks {
            selectTab(views.telegramTabContacts)
            views.toolbar.title = ""
            views.toolbar.title = getString(R.string.telegram_tab_contacts)
            childFragmentManager.commitTransaction {
                replace(R.id.roomListContainer, ContactsBookFragment())
                addToBackStack("telegram_contacts")
            }
        }

        childFragmentManager.addOnBackStackChangedListener {
            val contactsVisible = childFragmentManager.findFragmentById(R.id.roomListContainer) is ContactsBookFragment
            // FragmentManager outlives this view; don't access view binding after onDestroyView.
            if (!contactsVisible && isAdded && view != null) {
                views.toolbar.title = ""
                views.toolbar.title = getString(R.string.telegram_home_title)
                selectTab(views.telegramTabChats)
            }
        }

        views.telegramTabSettings.debouncedClicks {
            selectTab(views.telegramTabSettings)
            navigator.openSettings(requireContext())
        }

        views.telegramTabProfile.debouncedClicks {
            selectTab(views.telegramTabProfile)
            withState(viewModel) { state ->
                val ownUserId = state.myMatrixItem?.id
                if (ownUserId.isNullOrBlank()) {
                    navigator.openSettings(requireContext())
                } else {
                    navigator.openRoomMemberProfile(ownUserId, null, requireContext())
                }
            }
        }
    }

    private fun setupTelegramHomeTabs() {
        val tabs = views.telegramHomeTabs
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (isSyncingTelegramHomeTabs) return
                val filter = tab.tag as? HomeRoomFilter ?: return
                homeRoomListViewModel.handle(HomeRoomListAction.ChangeRoomFilter(filter))
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        homeRoomListViewModel.onEach(HomeRoomListViewState::headersData) { headers ->
            // Match the screenshot: two equal-looking All Chats segments, each still
            // wired to a real Matrix filter instead of a decorative, non-functional tab.
            val filters = listOf(HomeRoomFilter.ALL, HomeRoomFilter.PEOPlE)
            isSyncingTelegramHomeTabs = true
            try {
                tabs.removeAllTabs()
                tabs.isVisible = true
                filters.forEach { filter ->
                    tabs.addTab(
                            tabs.newTab()
                                    .setText(CommonStrings.all_chats)
                                    .setTag(filter),
                            false
                    )
                }
                val selectedIndex = filters.indexOf(headers.currentFilter).takeIf { it >= 0 } ?: 0
                tabs.getTabAt(selectedIndex)?.select()
            } finally {
                isSyncingTelegramHomeTabs = false
            }
        }
    }

    private fun setupObservers() {
        sharedRoomListActionViewModel = activityViewModelProvider[RoomListSharedActionViewModel::class.java]

        sharedRoomListActionViewModel
                .stream()
                .onEach(::handleSharedAction)
                .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun handleSharedAction(action: RoomListSharedAction) {
        when (action) {
            RoomListSharedAction.CloseBottomSheet -> spaceListBottomSheet.dismiss()
        }
    }

    private fun setupFabs() {
        showFABs()

        views.newLayoutCreateChatButton.debouncedClicks {
            newChatBottomSheet.takeIf { !it.isAdded }?.show(requireActivity().supportFragmentManager, NewChatBottomSheet.TAG)
        }

        // The smaller camera shortcut opens the platform camera without fabricating a Matrix upload.
        views.telegramCameraFab.debouncedClicks {
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            runCatching {
                startActivity(cameraIntent)
            }.onFailure {
                Toast.makeText(
                        requireContext(),
                        R.string.telegram_camera_unavailable,
                        Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showFABs() {
        // One primary compose action, as in Telegram. Spaces stays available in the toolbar menu.
        views.newLayoutCreateChatButton.show()
    }

    private fun setCurrentSpace(spaceId: String?) {
        spaceStateHandler.setCurrentSpace(spaceId, isForwardNavigation = false)
        sharedActionViewModel.post(HomeActivitySharedAction.OnCloseSpace)
    }

    override fun onDestroyView() {
        homeSearchTextWatcher?.let { watcher -> views.telegramHomeSearchInput.removeTextChangedListener(watcher) }
        homeSearchTextWatcher = null
        avatarRenderer.clear(views.telegramTabProfileIcon)
        currentCallsViewPresenter.unBind()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        callManager.checkForProtocolsSupportIfNeeded()
        refreshSpaceState()
        migrationBannerViewModel.handle(MigrationBannerAction.OnResume)
    }

    private fun refreshSpaceState() {
        spaceStateHandler.getCurrentSpace()?.let {
            onSpaceChange(it)
        }
    }

    private fun promptForNewUnknownDevices(uid: String, state: UnknownDevicesState, newest: DeviceInfo) {
        val user = state.myMatrixItem
        alertManager.postVectorAlert(
                VerificationVectorAlert(
                        uid = uid,
                        title = getString(CommonStrings.new_session),
                        description = getString(CommonStrings.verify_this_session, newest.displayName ?: newest.deviceId ?: ""),
                        iconId = R.drawable.ic_shield_warning
                ).apply {
                    viewBinder = VerificationVectorAlert.ViewBinder(user, avatarRenderer)
                    colorInt = colorProvider.getColorFromAttribute(com.google.android.material.R.attr.colorPrimary)
                    contentAction = Runnable {
                        (weakCurrentActivity?.get() as? VectorBaseActivity<*>)?.let { vectorBaseActivity ->
                            vectorBaseActivity.navigator
                                    .requestSessionVerification(vectorBaseActivity, newest.deviceId ?: "")
                        }
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreNewLogin(newest.deviceId?.let { listOf(it) }.orEmpty())
                        )
                    }
                    dismissedAction = Runnable {
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreNewLogin(newest.deviceId?.let { listOf(it) }.orEmpty())
                        )
                    }
                }
        )
    }

    private fun promptToReviewChanges(uid: String, state: UnknownDevicesState, oldUnverified: List<DeviceInfo>) {
        val user = state.myMatrixItem
        alertManager.postVectorAlert(
                VerificationVectorAlert(
                        uid = uid,
                        title = getString(CommonStrings.review_unverified_sessions_title),
                        description = getString(CommonStrings.review_unverified_sessions_description),
                        iconId = R.drawable.ic_shield_warning,
                        shouldBeDisplayedIn = { activity ->
                            // do not show when there is an ongoing verification flow
                            if (activity is VectorBaseActivity<*>) {
                                activity.supportFragmentManager.findFragmentByTag(SelfVerificationBottomSheet.TAG) == null &&
                                        activity !is QrCodeScannerActivity
                            } else true
                        }
                ).apply {
                    viewBinder = VerificationVectorAlert.ViewBinder(user, avatarRenderer)
                    colorInt = colorProvider.getColorFromAttribute(com.google.android.material.R.attr.colorPrimary)
                    contentAction = Runnable {
                        (weakCurrentActivity?.get() as? VectorBaseActivity<*>)?.let { activity ->
                            // mark as ignored to avoid showing it again
                            unknownDeviceDetectorSharedViewModel.handle(
                                    UnknownDeviceDetectorSharedViewModel.Action.IgnoreDevice(oldUnverified.mapNotNull { it.deviceId })
                            )
                            activity.navigator.openSettings(activity, EXTRA_DIRECT_ACCESS_SECURITY_PRIVACY_MANAGE_SESSIONS)
                        }
                    }
                    dismissedAction = Runnable {
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreDevice(oldUnverified.mapNotNull { it.deviceId })
                        )
                    }
                }
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onSpaceChange(spaceSummary: RoomSummary?) {
        // Space switching remains available from the menu; the main-screen brand stays stable.
        views.toolbar.title = ""
        views.toolbar.title = getString(R.string.telegram_home_title)
    }

    private fun setupKeysBackupBanner() {
        serverBackupStatusViewModel.handle(ServerBackupStatusAction.OnBannerDisplayed)
        serverBackupStatusViewModel
                .onEach {
                    when (val banState = it.bannerState.invoke()) {
                        is BannerState.Setup,
                        BannerState.BackingUp,
                        BannerState.Hidden -> views.homeKeysBackupBanner.render(banState, false)
                        null -> views.homeKeysBackupBanner.render(BannerState.Hidden, false)
                        else -> Unit /* No op? */
                    }
                }
        views.homeKeysBackupBanner.delegate = this
    }

    private fun setupVerificationBanner() {
        views.homeVerifyDevice.delegate = this
    }

    private fun setupActiveCallView() {
        currentCallsViewPresenter.bind(views.currentCallsView, this)
    }

    private fun setupToolbar() {
        setupToolbar(views.toolbar)
        // One fixed toolbar with a single title matches the Telegram chat-list header.
        views.toolbar.title = getString(R.string.telegram_home_title)
        views.toolbar.navigationIcon = null
        views.toolbar.setNavigationOnClickListener(null)
    }

    private fun setupMigrationBanner() {
        val bannerContainer = views.viewDownloadReplacementAppRoomListContainer
        val icon = bannerContainer.findViewById<ImageView>(R.id.view_download_replacement_app_room_list_icon)
        val title = bannerContainer.findViewById<TextView>(R.id.view_download_replacement_app_room_list_title)
        val body = bannerContainer.findViewById<TextView>(R.id.view_download_replacement_app_room_list_body)
        val button = bannerContainer.findViewById<Button>(R.id.view_download_replacement_app_room_list_button)
        bannerContainer.findViewById<View>(
                R.id.view_download_replacement_app_room_list_button_close
        ).debouncedClicks {
            migrationBannerViewModel.handle(MigrationBannerAction.Close)
        }
        migrationBannerViewModel
                .onEach {
                    when (val bannerState = it.bannerState) {
                        MigrationBannerState.Hide -> bannerContainer.isGone = true
                        is MigrationBannerState.Show -> {
                            bannerContainer.isVisible = true
                            // Only advertise the Element X logo when the target really is Element X.
                            icon.isVisible = bannerState.isElementX
                            title.text = bannerState.title
                            body.text = bannerState.body
                            button.isVisible = bannerState.showButton
                            button.text = bannerState.buttonText
                            button.setOnClickListener {
                                openApplicationStore(
                                        activity = requireActivity(),
                                        buildMeta = buildMeta,
                                        appId = bannerState.targetAppId,
                                )
                            }
                        }
                    }
                }
    }

    private fun setupDebugButton() {
        // Do not let the developer overlay alter the production-matched home toolbar.
        // Debug tools remain available from their dedicated diagnostic entry points.
        views.debugButton.isGone = true
    }

    /* ==========================================================================================
     * KeysBackupBanner Listener
     * ========================================================================================== */

    override fun onCloseClicked() {
        serverBackupStatusViewModel.handle(ServerBackupStatusAction.OnBannerClosed)
    }

    override fun setupKeysBackup() {
        navigator.openKeysBackupSetup(requireActivity(), false)
    }

    override fun recoverKeysBackup() {
        navigator.openKeysBackupManager(requireActivity())
    }

    override fun invalidate() = withState(viewModel) {
        views.syncStateView.render(
                it.syncState,
                it.incrementalSyncRequestState,
                it.pushCounter,
                vectorPreferences.developerShowDebugInfo()
        )
        views.homeVerifyDevice.isVisible = it.showVerifyDeviceBanner
        hasUnreadRooms = it.hasUnreadMessages
    }

    override fun onTapToReturnToCall() {
        callManager.getCurrentCall()?.let { call ->
            VectorCallActivity.newIntent(
                    context = requireContext(),
                    callId = call.callId,
                    signalingRoomId = call.signalingRoomId,
                    otherUserId = call.mxCall.opponentUserId,
                    isIncomingCall = !call.mxCall.isOutgoing,
                    isVideoCall = call.mxCall.isVideoCall,
                    mode = null
            ).let {
                startActivity(it)
            }
        }
    }

    override fun onBackPressed(toolbarButton: Boolean) = if (spaceStateHandler.isRoot()) {
        false
    } else {
        val lastSpace = spaceStateHandler.popSpaceBackstack()
        spaceStateHandler.setCurrentSpace(lastSpace, isForwardNavigation = false)
        true
    }

    private fun SpaceStateHandler.isRoot() = getSpaceBackstack().isEmpty()

    override fun onVerificationLearnMoreClicked() {
        openUrlInChromeCustomTab(
                context = requireContext(),
                session = null,
                url = "https://docs.element.io/latest/element-support/device-verification/how-to-verify-devices",
        )
    }

    override fun onVerifySession() {
        (activity as? VectorBaseActivity<*>)?.let { vectorBaseActivity ->
            vectorBaseActivity.navigator
                    .requestSelfSessionVerification(vectorBaseActivity)
        }
    }

    companion object {
        private const val HOME_ROOM_LIST_FRAGMENT_TAG = "TAG_HOME_ROOM_LIST"
    }
}
