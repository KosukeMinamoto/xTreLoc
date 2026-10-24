package com.treloc.xtreloc.app.gui.service;

import edu.sc.seis.seisFile.sac.SacTimeSeries;
import java.util.ArrayList;
import java.util.List;

/** An automatic absolute-time picker. It only proposes; accepting a proposal is up to the caller. */
public interface AutoPicker {

    /** Short name shown in the GUI and stored with each proposal. */
    String name();

    /** Proposes P and S picks for the stations present in {@code traces}. */
    List<PickProposal> propose(SacTimeSeries[] traces);

    /** Maeda (1985) AIC picker. */
    static AutoPicker aic(AicPicker.Params params) {
        return new AutoPicker() {
            @Override
            public String name() {
                return "AIC";
            }

            @Override
            public List<PickProposal> propose(SacTimeSeries[] traces) {
                return toProposals(AicPicker.pickStations(traces, params), name());
            }
        };
    }

    /** Kurtosis picker (Baillard et al., 2014, simplified). */
    static AutoPicker kurtosis(KurtosisPicker.Params params) {
        return new AutoPicker() {
            @Override
            public String name() {
                return "Kurtosis";
            }

            @Override
            public List<PickProposal> propose(SacTimeSeries[] traces) {
                return toProposals(KurtosisPicker.pickStations(traces, params), name());
            }
        };
    }

    /**
     * EQTransformer. For an event window it proposes, per station and phase, the peak with the highest
     * probability. (For long records use {@link EqtPicker#pickAll} and export all peaks.)
     */
    static AutoPicker eqt(EqtPicker.Params params) {
        return new AutoPicker() {
            @Override
            public String name() {
                return "EQTransformer";
            }

            @Override
            public List<PickProposal> propose(SacTimeSeries[] traces) {
                try (EqtPicker picker = new EqtPicker(params)) {
                    List<PickProposal> out = new ArrayList<>();
                    for (EqtPicker.Pick p : EqtPicker.best(picker.pickAll(traces, null))) {
                        out.add(new PickProposal(p.station(), p.component(), p.phase(), p.time(), name(),
                            p.prob()));
                    }
                    return out;
                }
            }
        };
    }

    static List<PickProposal> toProposals(List<AicPicker.StationPick> picks, String method) {
        List<PickProposal> out = new ArrayList<>();
        for (AicPicker.StationPick p : picks) {
            if (p.pTime != null) {
                out.add(new PickProposal(p.station, p.pComponent, "P", p.pTime, method));
            }
            if (p.sTime != null) {
                out.add(new PickProposal(p.station, p.sComponent, "S", p.sTime, method));
            }
        }
        return out;
    }
}
