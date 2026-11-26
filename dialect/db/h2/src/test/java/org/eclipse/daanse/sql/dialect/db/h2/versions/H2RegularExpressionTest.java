/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.h2.versions;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MDX {@code MATCHES} is Java's {@code Pattern.matches}: the whole value, by Java's own expression. H2's
 * {@code REGEXP_LIKE} is java.util.regex, on every version from 1.4.200 on, and finds rather than matches. The dialect
 * is built from a live connection of each version, and the condition it writes must pick the rows Java picks: anchored
 * at both ends, the case kept or ignored, ASCII only or beyond with {@code u}, a flag inside the expression, a dot
 * escaped or taking no line break unless {@code s}, {@code \Q…\E} quoting what is in it, lookaround, a back
 * reference, a possessive quantifier, a quote in the expression, a NULL never; and an expression Java cannot read
 * gives none.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class H2RegularExpressionTest {

    private static final List<String> WORDS = Arrays.asList("apple", "Apple", "APPLE", "pineapple", "banana", "a.b",
            "axb", "a\nb", "a+b", "aab", "a1 b", "ÄPFEL", "äpfel", "it's", "aaa", null);

    private static final List<String> EXPRESSIONS = List.of("apple", "a.*", "(?i)a.*", "(?i)apple", ".*apple",
            "A.*", "a\\.b", "a.b", "(?s)a.b", "[a-c].*", "ban(an)+a", "apple|banana", "\\Qa+b\\E", "a+b",
            "\\w\\d\\s\\w", "(?i:a)pple", "(?i)äpfel", "(?iu)äpfel", "a(?=p).*", "(?<=a)pple|banana", "(a)\\1.*",
            "a++", "it's", "[a-z&&[^b]]+", "(?x) a p p l e");

    @ParameterizedTest(name = "h2 {0}")
    @ValueSource(strings = { "1.4.200", "2.0.206", "2.1.214", "2.2.224", "2.3.232" })
    void regularExpression(String version) throws Exception {
        try (H2Versions.Session h2 = H2Versions.open(version)) {
            h2.execute("CREATE TABLE WORDS(ID INT PRIMARY KEY, W VARCHAR(20))");
            try (PreparedStatement ps = h2.connection().prepareStatement("INSERT INTO WORDS VALUES (?, ?)")) {
                for (int i = 0; i < WORDS.size(); i++) {
                    ps.setInt(1, i);
                    ps.setString(2, WORDS.get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            assertThat(h2.dialect().allowsRegularExpressionInWhereClause()).isTrue();

            for (String javaRegex : EXPRESSIONS) {
                Pattern p = Pattern.compile(javaRegex);
                List<String> java = WORDS.stream().filter(w -> w != null && p.matcher(w).matches()).toList();
                String condition = h2.dialect().regexGenerator().generateRegularExpression("W", javaRegex)
                        .orElseThrow().toString();
                assertThat(matching(h2, condition)).as(javaRegex + " as Java: " + condition)
                        .containsExactlyElementsOf(java);
            }
            assertThat(h2.dialect().regexGenerator().generateRegularExpression("W", "a(")).as("not Java's")
                    .isEmpty();
        }
    }

    private static List<String> matching(H2Versions.Session h2, String condition) throws Exception {
        List<String> out = new ArrayList<>();
        try (var st = h2.statement(); ResultSet rs = st.executeQuery("SELECT W FROM WORDS WHERE " + condition
                + " ORDER BY ID")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
