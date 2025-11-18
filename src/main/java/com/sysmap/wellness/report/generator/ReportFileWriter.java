package com.sysmap.wellness.report.generator;

import com.sysmap.wellness.utils.LoggerUtils;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Responsável exclusivamente por gravar o {@link Workbook}
 * em disco, isolando a lógica de I/O de arquivo.
 */
public class ReportFileWriter {

    /**
     * Salva um Workbook Excel no caminho especificado.
     *
     * @param wb        Workbook a ser gravado.
     * @param finalPath Caminho destino.
     * @throws IOException Se houver falha ao escrever o arquivo.
     */
    public void saveWorkbook(Workbook wb, Path finalPath) throws IOException {

        try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
            wb.write(fos);
        }

        LoggerUtils.success("💾 Excel salvo em " + finalPath);
    }
}
