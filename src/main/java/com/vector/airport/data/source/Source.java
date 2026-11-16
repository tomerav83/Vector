package com.vector.airport.data.source;

import com.vector.airport.data.web.Web;
import java.net.URI;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * One source, as a country's profile declares it under {@code vector.data.sources}: how it is cited, the template of
 * its files, where it names its releases, and how many of them it loads.
 *
 * <p>A period is the day one release starts, e.g. {@code 2026-01-01}, one its {@link Livecheck} page names; it loads
 * the newest, and with {@code history} that many releases before it. A source without a livecheck publishes only its
 * current state, period {@link #CURRENT}. Each period is one file: the template with its placeholders filled from the
 * period's day, {@code {date}}, {@code {year}}, {@code {quarter}}, and {@code {from}} and {@code {to}} (its year's
 * January and December), see {@link Template}.
 *
 * @param title its publisher and name, as an answer cites it, e.g. {@code FAA Terminal Area Forecast}
 * @param page the publisher's page that describes it, which the citation links to
 * @param template its file, with the placeholders each period fills
 * @param livecheck the page that names its newest release; none: only its current state
 * @param history releases before the newest that it loads too
 */
public record Source(
        String title,
        URI page,
        SourceFile template,
        @Nullable Livecheck livecheck,
        int history) {

    public static final String CURRENT = "current";

    /**
     * As a profile declares it: the file's keys sit beside {@code livecheck} and {@code history}.
     *
     * @param url where a file is published (template)
     * @param downloader the {@code Downloader} bean that downloads its files
     * @param form the choices an on-demand download posts (templates)
     * @param format whether its files are web pages that hold the data ({@link SourceFile#format()})
     */
    @ConstructorBinding
    public Source(
            String title,
            URI page,
            String url,
            @DefaultValue(SourceFile.STORED) String downloader,
            @DefaultValue Form form,
            @Nullable Livecheck livecheck,
            int history,
            @DefaultValue(SourceFile.TABLE) String format) {
        this(title, page, new SourceFile(downloader, url, form, format), livecheck, history);
    }

    /**
     * Fills the template with a sample day, so a typo in a placeholder fails the start, naming the source's key.
     *
     * @throws NullPointerException without a title or page
     */
    public Source {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(page, "page");
        template.render(livecheck == null ? Map.of() : context(LocalDate.EPOCH));
    }

    /** The periods it loads, oldest first, from what its livecheck page names on {@code web}; else {@link #CURRENT}. */
    public List<String> periods(Web web) {
        return Optional.ofNullable(livecheck)
                .map(livecheck -> livecheck.periods(web, history))
                .orElse(List.of(CURRENT));
    }

    /** The file of {@code period}, e.g. bts-segments' 2025 file, which posts {@code cboYear=2025}. */
    public SourceFile file(String period) {
        return template.render(period.equals(CURRENT) ? Map.of() : context(LocalDate.parse(period)));
    }

    /** A day's template context: its date, year, quarter, and its year's first and last month. */
    private static Map<String, Object> context(LocalDate day) {
        Year year = Year.from(day);
        return Map.of(
                "date", day,
                "year", year,
                "quarter", day.get(IsoFields.QUARTER_OF_YEAR),
                "from", year.atMonth(Month.JANUARY),
                "to", year.atMonth(Month.DECEMBER));
    }
}
