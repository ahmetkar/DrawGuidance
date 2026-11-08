package com.karapp.drawguidance.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * DrawGuidance sunucusu (v33): Spring Boot + MongoDB. İki iş yapar:
 *
 * <ul>
 *   <li><b>Katalog</b> (herkese açık, salt okunur): şekil şablonları (yapay zekâ eşleştirme tablosu), hazır vektör
 *       şekiller ve modların temel sinir ağı ağırlıkları. Açılışta jar içindeki tohum dosyasından eşitlenir.</li>
 *   <li><b>Kullanıcılar</b>: Firebase ID jetonuyla doğrulanan profil kaydı.</li>
 * </ul>
 *
 * Kullanıcı içerikleri (tuvaller, klasörler, resimler, 3B şekiller, varlıklar, eşitleme) sunucuda tutulmaz.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DrawGuidanceServerApplication {
    public static void main(String[] args) {
        // v43 yönetim komut satırı: java -jar drawguidance-server.jar cli <komut> (web sunucusu ve işçiler açılmaz)
        if (args.length > 0 && "cli".equals(args[0])) {
            System.exit(runCli(java.util.Arrays.copyOfRange(args, 1, args.length)));
        }
        SpringApplication.run(DrawGuidanceServerApplication.class, args);
    }

    static int runCli(String[] argv) {
        // Türkçe harfler: konsolun kodlamasına bakılmadan UTF-8 (yerel sunucu betiği de UTF-8 okur)
        java.io.PrintStream utf8 = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8);
        System.setOut(utf8);
        if (argv.length == 0 || java.util.Set.of("yardim", "help", "-h", "--help").contains(argv[0])) {
            com.karapp.drawguidance.server.distribution.cli.DistributionCli.help(System.out);
            return argv.length == 0 ? 2 : 0;
        }
        try (var ctx = new org.springframework.boot.builder.SpringApplicationBuilder(DrawGuidanceServerApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .bannerMode(org.springframework.boot.Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties("drawguidance.distribution.workers-enabled=false", "drawguidance.distribution.seed-base-packages=false", "logging.level.root=WARN", "logging.level.com.karapp=WARN")
                .run()) {
            return ctx.getBean(com.karapp.drawguidance.server.distribution.cli.DistributionCli.class).run(argv, System.out);
        }
    }
}
