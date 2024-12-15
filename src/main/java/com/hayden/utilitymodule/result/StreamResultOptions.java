package com.hayden.utilitymodule.result;

import lombok.Builder;

@Builder
public record StreamResultOptions(boolean cache, boolean empty, boolean isInfinite, boolean isNonEmpty, boolean hasErr, boolean hasRes) {
}
