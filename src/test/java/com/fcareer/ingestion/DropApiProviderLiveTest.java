package com.fcareer.ingestion;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.mapper.AcceleRateDeriver;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import com.fcareer.ingestion.provider.DropApiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "drop.live", matches = "true")
class DropApiProviderLiveTest {

    @Test
    void fetchesAndMapsRealPage() {
        DropApiProvider provider = new DropApiProvider(new IngestionProperties());

        ExternalPlayerPage page = provider.fetch(0, 20);

        assertThat(page.totalItems()).isGreaterThan(1000);
        assertThat(page.items()).hasSizeGreaterThan(15);
        ExternalPlayer top = page.items().get(0);
        assertThat(top.attrs()).hasSize(Attr.values().length);
        assertThat(top.positions()).isNotEmpty();
        assertThat(top.dob()).isNotNull();
        assertThat(top.preferredFoot()).isIn("Left", "Right");
        assertThat(top.playstyles()).isNotEmpty();
        assertThat(new AcceleRateDeriver(new FitEngineProperties()).derive(top.heightCm(), top.attrs())).isNotNull();
        System.out.println("LIVE " + top.fullName() + " ovr=" + top.overall() + " pos=" + top.positions()
                + " foot=" + top.preferredFoot() + " ps=" + top.playstyles() + " club=" + top.clubName() + "/" + top.leagueName());
    }
}
