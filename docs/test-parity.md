# Test parity with SovraiOS

Every Jest test in SovraiOS's `src/**/__tests__` and the JUnit test that covers the same
case here (`app/src/test/java/ai/sovra/remote/`). Some Kotlin tests cover two SovraiOS
cases; none of the SovraiOS cases is left out. Keep this table current when either side changes.

| SovraiOS test (file › name) | Kotlin test |
| --- | --- |
| client › pairs once, then reconnects with Hello without a new invitation | ClientTest.pairsOnceThenReconnectsWithHelloWithoutANewInvitation |
| client › uses up a fresh code when an already-paired phone scans it again | ClientTest.usesUpAFreshCodeWhenAnAlreadyPairedPhoneScansItAgain |
| client › stays paired when the code is already used or refused after Hello | ClientTest.staysPairedWhenTheCodeIsAlreadyUsedAfterHello |
| client › reports a wrong invitation and never treats unreachable as revoked | ClientTest.reportsAWrongInvitationAndNeverTreatsUnreachableAsRevoked |
| client › uses exact sequences starting at 1 and inspects before every mutation | ClientTest.usesExactSequencesStartingAtOneAndInspectsBeforeEveryMutation |
| client › retries only stale observations, which the desktop never delivers | ClientTest.retriesOnlyStaleObservationsWhichTheDesktopNeverDelivers |
| client › never replays a mutation whose reply was lost | ClientTest.neverReplaysAMutationWhoseReplyWasLost |
| client › reconnects reads after the desktop closes an idle connection | ClientTest.reconnectsReadsAfterTheDesktopClosesAnIdleConnection |
| client › re-finds a control by label after reconnecting changes target IDs | ClientTest.refindsAControlByLabelAfterReconnectingChangesTargetIds |
| client › stops on disabled desktops and asks for a fresh look | ProtocolTest.reportsADisabledComputer |
| client › types through the real composer, then presses Enter | ClientTest.typesThroughTheRealComposerThenPressesEnter |
| client › runs a bare /skill instead of only completing its name | ScreensTest.sendsThroughTheRealChatBoxAndRunsABareSkill |
| client › types a /skill with words after it unchanged | ClientTest.typesASkillWithWordsAfterItUnchanged |
| client › types into a chosen field on the computer without pressing Enter | ClientTest.typesIntoAChosenFieldWithoutPressingEnter |
| client › never mistakes a form field for the chat box | ClientTest.neverMistakesAFormFieldOrSearchBoxForTheChatBox |
| client › never types into a search box | ClientTest.neverMistakesAFormFieldOrSearchBoxForTheChatBox |
| client › prefers Sovra's composer over other text inputs | ClientTest.neverMistakesAFormFieldOrSearchBoxForTheChatBox |
| client › opens Chat on the computer when no chat box is showing | ScreensTest.opensChatFirstWhenAnotherPageIsShowing |
| client › stops after opening Chat if there is still no chat box | ClientTest.stopsAfterOpeningChatIfThereIsStillNoChatBox |
| client › explains a missing chat box instead of typing blindly | ParityTest.explainsAMissingChatBoxInsteadOfTypingBlindly |
| client › presses the visible Stop control, or Escape | ClientTest.pressesOnlyTheRealStopControlOrEscape |
| client › never mistakes other "Stop …" buttons for stopping work | ClientTest.pressesOnlyTheRealStopControlOrEscape |
| client › splits long text within the 4 KiB limit | ClientTest.splitsLongTextWithinTheLimitAndCleansPhoneNames |
| client › cleans phone names for the desktop | ClientTest.splitsLongTextWithinTheLimitAndCleansPhoneNames |
| desktop › reads the conversation as messages, in order | ReadersTest.readsTheConversationInOrderAndKeepsPartlyScrolledReplies |
| desktop › lists the open page's controls before the sidebar, without the page shortcuts | ReadersTest.listsTheOpenPagesControlsFirst |
| desktop › knows when the conversation continues above what is shown | ReadersTest.readsTheConversationInOrderAndKeepsPartlyScrolledReplies |
| desktop › keeps a long reply that is partly scrolled out of the conversation | ReadersTest.readsTheConversationInOrderAndKeepsPartlyScrolledReplies |
| desktop › lists the chats shown in the sidebar | ReadersTest.listsChatsPagesAndDestinations |
| desktop › lists the sidebar pages (not New chat, chats or the tour) | ReadersTest.listsChatsPagesAndDestinations |
| desktop › finds sidebar destinations by exact label only | ReadersTest.listsChatsPagesAndDestinations |
| elements › finds the conversation log | ParityTest.findsTheConversationLogOrNone |
| elements › reads only text inside the conversation when there is one | ReadersTest.readsOnlyTextInsideTheConversation, ParityTest.readsAllTextInReadingOrderWithoutAConversation |
| navigate › scrolls a chat out from under the sidebar footer before opening it | ParityTest.scrollsAChatOutFromUnderTheSidebarFooterBeforeOpeningIt |
| navigate › opens a visible chat directly | ParityTest.opensAVisibleChatDirectly |
| navigate › scrolls down to a chat Sovra keeps just outside its list | ReadersTest.scrollsDownToAChatJustOutsideTheList |
| navigate › finds a chat that is not in the list by searching, then clears the search | ScreensTest.findsAChatThatIsNotListedBySearching |
| navigate › says so when a chat cannot be found at all | ReadersTest.saysSoWhenAChatCannotBeFound |
| navigate › searches and scrolls the chat list | ReadersTest.searchesAndScrollsTheChatList |
| navigate › brings up Sovra's chat only when another page is showing | ReadersTest.bringsUpChatOnlyWhenAnotherPageIsShowing |
| skills › reads every skill in the menu, including rows scrolled out of view | ScreensTest.readsEverySkillAndRanksLikeTheDesktop |
| skills › keeps the part of a specialist's description people need | ReadersTest.keepsTheUsefulPartOfASpecialistsDescription |
| skills › ranks matches like the desktop menu | ScreensTest.readsEverySkillAndRanksLikeTheDesktop, ParityTest.ranksSkillsByNameWordAndDescription |
| skills › only treats a leading command still being typed as a / query | ScreensTest.readsEverySkillAndRanksLikeTheDesktop, ParityTest.ranksSkillsByNameWordAndDescription |
| skills › explains Sovra's skill notices in plain words | ScreensTest.readsEverySkillAndRanksLikeTheDesktop, ParityTest.ranksSkillsByNameWordAndDescription |
| skills › opens the menu on the computer, reads it, and leaves the chat box empty | ScreensTest.loadsSkillsFromTheMenuAndLeavesTheChatBoxEmpty |
| ticket › decodes a link produced by the desktop encoder | ProtocolTest.decodesAPairingLinkFromTheDesktopEncoder |
| ticket › rejects everything the desktop decoder rejects | ParityTest.rejectsEveryPairingLinkTheDesktopRejects |
| ticket › accepts only real socket addresses | ParityTest.acceptsOnlyRealSocketAddresses |
| wire › requests built by the client match the desktop fixtures exactly | ProtocolTest.requestsMatchTheDesktopFixturesByteForByte |
| wire › decodes desktop replies strictly | ProtocolTest.decodesDesktopRepliesStrictly |
| wire › parses inspect payloads and drops malformed elements | ParityTest.parsesInspectPayloadsAndDropsMalformedElements |
| wire › validates input like UiRequest::validate | ProtocolTest.validatesInputLikeTheDesktop |
| markdown › splits headings, paragraphs, lists, code and tables | ScreensTest.parsesRepliesIntoBlocks |
| markdown › marks bold, italic and inline code | ParityTest.marksBoldItalicAndInlineCode |
| markdown › leaves plain text alone | ParityTest.leavesPlainTextAlone |
| connection-status › shows each step as it happens | ConnectionStatusTest.showsEachStepAsItHappens |
| connection-status › explains slow first tries without blaming the person | ConnectionStatusTest.explainsSlowFirstTries |
| connection-status › counts down to the next try and offers to try now | ConnectionStatusTest.countsDownToTheNextTryAndOffersToTryNow |
| connection-status › keeps the last failure in view while trying again | ConnectionStatusTest.keepsTheLastFailureInViewWhileTryingAgain |
| connection-status › lists what to check after repeated failures | ConnectionStatusTest.listsWhatToCheckAfterRepeatedFailures |
| connection-status › stays quiet for a quick reconnect, then explains | ConnectionStatusTest.staysQuietForAQuickReconnectThenExplains |
| connection-status › names the path in plain words | ConnectionStatusTest.namesThePathInPlainWords |
| connection-status › keeps counting and explains a slow pairing | ConnectionStatusTest.keepsCountingAndExplainsASlowPairing |

Android-only: `ReadersTest.keepsEveryChatSeenInTheSidebarsOrder` (the Chats tab's merge, which
lives in a component in SovraiOS), `ProtocolTest.pairsThenControlsWithFreshRevisions`,
`ProtocolTest.neverReplaysInputWhoseReplyWasLost`, and the on-device
`ZoomBoxTest.pinchZoomsInAndTapsStillReachTheControls`.

SovraiOS-only: the two store tests in `src/state/__tests__/remote.test.ts` (failed tries are
counted and the route is reported; "Try now" never starts a second try). `RemoteStore` here
needs an Android `Context` for its storage, so its progress logic was checked on the emulator
instead; the wording it shows is covered by `ConnectionStatusTest`.
