package com.iet.dashboard.academics.service;

import com.iet.dashboard.academics.web.ParsedSyllabusDto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns extracted syllabus text into units and topics.
 *
 * <p>Deliberately heuristic and dependency-free: institutional course descriptions are laid
 * out as "Unit N: Title" headings followed by numbered or bulleted topics, with their own
 * tables of contents, textbook lists and page furniture mixed in. Anything ambiguous is
 * dropped rather than guessed at, because the parsed result is always shown as a preview
 * before it is saved.
 */
public final class SyllabusTextParser {

    /** Total teaching weeks used to spread topics across the term. */
    private static final int TERM_WEEKS = 11;
    private static final int MAX_TOPIC_LENGTH = 500;

    private static final Pattern UNIT_HEADING = Pattern.compile(
            "^\\s*(?:unit|module|chapter)\\s*[-–—:.]?\\s*([0-9]{1,2}|[ivxlc]{1,6})\\b[:\\-–—.]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBERED_TOPIC = Pattern.compile(
            "^\\s*\\(?(\\d{1,2}(?:\\.\\d{1,2}){0,3})[.)\\]]?\\s*[-–—:]?\\s+(\\S.{2,})$");

    private static final Pattern BULLET_TOPIC = Pattern.compile(
            "^\\s*[•▪●◦*\\-–—]\\s+(\\S.{2,})$");

    private static final Pattern LETTERED_TOPIC = Pattern.compile(
            "^\\s*[a-z][.)]\\s+(\\S.{2,})$", Pattern.CASE_INSENSITIVE);

    /** Lines that announce a trailing appendix section whose contents are not topics. */
    private static final Pattern NOISE_SECTION = Pattern.compile(
            "^\\s*(text\\s*books?|reference\\s*books?|references|suggested\\s*readings?|"
                    + "course\\s*outcomes?|learning\\s*outcomes?|assessment|evaluation\\s*scheme|"
                    + "laboratory\\s*requirements?|prerequisites?)\\b.*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PAGE_FURNITURE = Pattern.compile(
            "^\\s*(page\\s*)?\\d{1,3}\\s*$", Pattern.CASE_INSENSITIVE);

    private SyllabusTextParser() {
    }

    public static ParsedSyllabusDto parse(String rawText, String sourceFileName) {
        if (rawText == null || rawText.isBlank()) {
            return new ParsedSyllabusDto(sourceFileName, 0, List.of());
        }

        String[] rawLines = rawText.split("\\r?\\n");

        List<Integer> unitNumbers = new ArrayList<>();
        List<String> unitTitles = new ArrayList<>();
        List<List<String>> unitTopics = new ArrayList<>();

        boolean inNoiseSection = false;
        int currentUnit = -1;

        for (String rawLine : rawLines) {
            String line = normalize(rawLine);
            if (line.isEmpty() || PAGE_FURNITURE.matcher(line).matches()) {
                continue;
            }

            if (NOISE_SECTION.matcher(line).matches()) {
                inNoiseSection = true;
                continue;
            }

            Matcher unitMatch = UNIT_HEADING.matcher(line);
            if (unitMatch.matches()) {
                inNoiseSection = false;
                int number = toNumber(unitMatch.group(1));
                String title = clean(unitMatch.group(2));

                // A table-of-contents repeat of a unit we already saw is not a new unit.
                if (unitNumbers.contains(number) && title.isEmpty()) {
                    continue;
                }

                unitNumbers.add(number);
                unitTitles.add(title.isEmpty() ? "Unit " + number : title);
                unitTopics.add(new ArrayList<>());
                currentUnit = unitNumbers.size() - 1;
                continue;
            }

            if (inNoiseSection) {
                continue;
            }

            String topic = extractTopic(line);
            if (topic == null) {
                continue;
            }

            if (currentUnit < 0) {
                // Topics before any unit heading: open an implicit first unit.
                unitNumbers.add(1);
                unitTitles.add("Unit 1");
                unitTopics.add(new ArrayList<>());
                currentUnit = 0;
            }
            unitTopics.get(currentUnit).add(topic);
        }

        List<ParsedSyllabusDto.Unit> units = new ArrayList<>();
        for (int i = 0; i < unitNumbers.size(); i++) {
            List<String> topics = unitTopics.get(i);
            if (topics.isEmpty()) {
                continue;
            }
            List<ParsedSyllabusDto.Topic> topicDtos = new ArrayList<>(topics.size());
            for (String topic : topics) {
                topicDtos.add(new ParsedSyllabusDto.Topic(topic, 0));
            }
            units.add(new ParsedSyllabusDto.Unit(unitNumbers.get(i), unitTitles.get(i), topicDtos));
        }

        return new ParsedSyllabusDto(sourceFileName, rawText.length(), assignTargetWeeks(units));
    }

    /**
     * Spreads topics evenly across the term so pacing has something to compare against.
     * Topic {@code n} of {@code total} lands on week {@code ceil(n * TERM_WEEKS / total)}.
     */
    private static List<ParsedSyllabusDto.Unit> assignTargetWeeks(List<ParsedSyllabusDto.Unit> units) {
        int total = units.stream().mapToInt(unit -> unit.topics().size()).sum();
        if (total == 0) {
            return units;
        }

        List<ParsedSyllabusDto.Unit> result = new ArrayList<>(units.size());
        int index = 0;
        for (ParsedSyllabusDto.Unit unit : units) {
            List<ParsedSyllabusDto.Topic> topics = new ArrayList<>(unit.topics().size());
            for (ParsedSyllabusDto.Topic topic : unit.topics()) {
                index++;
                int week = (int) Math.ceil((double) index * TERM_WEEKS / total);
                topics.add(new ParsedSyllabusDto.Topic(topic.topicName(), Math.min(TERM_WEEKS, Math.max(1, week))));
            }
            result.add(new ParsedSyllabusDto.Unit(unit.unitNumber(), unit.unitTitle(), topics));
        }
        return result;
    }

    private static String extractTopic(String line) {
        Matcher numbered = NUMBERED_TOPIC.matcher(line);
        if (numbered.matches()) {
            String candidate = clean(numbered.group(2));
            // Skip deeper sub-numbering such as "1.2.3.4" detail rows that are not topics.
            if (numbered.group(1).split("\\.").length <= 2 && !candidate.isEmpty()) {
                return truncate(candidate);
            }
            return null;
        }

        Matcher bulleted = BULLET_TOPIC.matcher(line);
        if (bulleted.matches()) {
            return truncate(clean(bulleted.group(1)));
        }

        Matcher lettered = LETTERED_TOPIC.matcher(line);
        if (lettered.matches()) {
            return truncate(clean(lettered.group(1)));
        }

        return null;
    }

    private static String normalize(String line) {
        return line.replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("[\\.\\s]{4,}", " ") // TOC dot leaders
                .trim();
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\.\\s]+$", "")
                .replaceAll("^[:\\-–—\\s]+", "")
                .trim();
    }

    private static String truncate(String value) {
        return value.length() <= MAX_TOPIC_LENGTH ? value : value.substring(0, MAX_TOPIC_LENGTH);
    }

    private static int toNumber(String token) {
        if (token == null || token.isBlank()) {
            return 1;
        }
        String value = token.trim().toUpperCase();
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = romanDigit(value.charAt(i));
            int next = (i + 1 < value.length()) ? romanDigit(value.charAt(i + 1)) : 0;
            total += (digit < next) ? -digit : digit;
        }
        return total > 0 ? total : 1;
    }

    private static int romanDigit(char symbol) {
        return switch (symbol) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            default -> 0;
        };
    }
}
