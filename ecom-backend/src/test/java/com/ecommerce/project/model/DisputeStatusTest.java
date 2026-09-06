package com.ecommerce.project.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeStatusTest {

    @Test
    void foldsStripeStatusStringsIntoTheMachine() {
        assertThat(DisputeStatus.fromStripe("warning_needs_response")).isEqualTo(DisputeStatus.NEEDS_RESPONSE);
        assertThat(DisputeStatus.fromStripe("needs_response")).isEqualTo(DisputeStatus.NEEDS_RESPONSE);
        assertThat(DisputeStatus.fromStripe("under_review")).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(DisputeStatus.fromStripe("won")).isEqualTo(DisputeStatus.WON);
        assertThat(DisputeStatus.fromStripe("lost")).isEqualTo(DisputeStatus.LOST);
        assertThat(DisputeStatus.fromStripe("warning_closed")).isEqualTo(DisputeStatus.CLOSED);
    }

    @Test
    void unknownStripeStatusStaysOnTheRadar() {
        assertThat(DisputeStatus.fromStripe("something_new")).isEqualTo(DisputeStatus.NEEDS_RESPONSE);
        assertThat(DisputeStatus.fromStripe(null)).isEqualTo(DisputeStatus.NEEDS_RESPONSE);
    }

    @Test
    void forwardTransitionsAreAllowed() {
        assertThat(DisputeStatus.NEEDS_RESPONSE.canTransitionTo(DisputeStatus.UNDER_REVIEW)).isTrue();
        assertThat(DisputeStatus.NEEDS_RESPONSE.canTransitionTo(DisputeStatus.WON)).isTrue();
        assertThat(DisputeStatus.UNDER_REVIEW.canTransitionTo(DisputeStatus.LOST)).isTrue();
    }

    @Test
    void terminalStatesGoNowhereAndSameStateIsANoOp() {
        assertThat(DisputeStatus.WON.isTerminal()).isTrue();
        assertThat(DisputeStatus.LOST.isTerminal()).isTrue();
        assertThat(DisputeStatus.CLOSED.isTerminal()).isTrue();

        assertThat(DisputeStatus.WON.canTransitionTo(DisputeStatus.NEEDS_RESPONSE)).isFalse();
        assertThat(DisputeStatus.LOST.canTransitionTo(DisputeStatus.WON)).isFalse();
        assertThat(DisputeStatus.WON.canTransitionTo(DisputeStatus.WON)).as("idempotent re-sync").isTrue();
    }

    @Test
    void underReviewCannotWalkBackToNeedsResponse() {
        assertThat(DisputeStatus.UNDER_REVIEW.canTransitionTo(DisputeStatus.NEEDS_RESPONSE)).isFalse();
    }
}
