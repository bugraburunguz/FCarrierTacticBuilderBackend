package com.fcareer.ingestion;

import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.service.OpenDatasetImporter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenDatasetImporterTest {

    @Test
    void refusesToImportUntilLicenseIsConfirmed() {
        OpenDatasetImporter importer = new OpenDatasetImporter(new IngestionProperties(), null, null);

        assertThatThrownBy(importer::importSeed).isInstanceOf(ImportParseException.class);
    }

    @Test
    void refusesWhenColumnMappingIsMissing() {
        IngestionProperties properties = new IngestionProperties();
        properties.getOpenDataset().setLicenseConfirmed(true);
        properties.getOpenDataset().setPath("x.csv");

        assertThatThrownBy(() -> new OpenDatasetImporter(properties, null, null).importSeed())
                .isInstanceOf(ImportParseException.class);
    }

    @Test
    void splitsQuotedCells() {
        List<String> cells = OpenDatasetImporter.splitLine("1,\"Doe, John\",\"say \"\"hi\"\"\",9", ',');

        assertThat(cells).containsExactly("1", "Doe, John", "say \"hi\"", "9");
    }
}
