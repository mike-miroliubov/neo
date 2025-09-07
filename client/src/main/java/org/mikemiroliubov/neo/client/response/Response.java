package org.mikemiroliubov.neo.client.response;

import java.util.Map;

public record Response(ResponseBody body, Map<String, String> headers, int statusCode, String reason) {
}
