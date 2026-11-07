package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ApiClientTest {
    @Test
    void encodeQueryEscapesReservedCharactersAndSpaces() {
        assertEquals("tra+s%E1%BB%AFa%26c%C3%A0+ph%C3%AA",
                ApiClient.encodeQuery("tra sữa&cà phê"));
    }
}
