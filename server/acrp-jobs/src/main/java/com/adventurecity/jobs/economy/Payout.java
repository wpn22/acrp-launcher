package com.adventurecity.jobs.economy;

/** Result of a payout attempt: what was earned, what the city took, what actually landed. */
public final class Payout {

    public final long gross;
    public final long tax;
    public final long net;
    public final boolean capReached;

    public Payout(long gross, long tax, long net, boolean capReached) {
        this.gross = gross;
        this.tax = tax;
        this.net = net;
        this.capReached = capReached;
    }

    public boolean paid() {
        return net > 0L;
    }
}
