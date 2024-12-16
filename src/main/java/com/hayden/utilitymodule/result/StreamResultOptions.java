package com.hayden.utilitymodule.result;

import lombok.Builder;

@Builder
public record StreamResultOptions(boolean empty, boolean isInfinite, boolean isNonEmpty, boolean hasErr, boolean hasRes) {
}
