package org.chenile.scheduler.errorcodes;

public enum ErrorCodes {
  DUPLICATE_SCHEDULE_ID(570),
  INVALID_SCHEDULE(571),
  CANNOT_SCHEDULE(572);

  final int subError;

  ErrorCodes(int subError) {
    this.subError = subError;
  }

  public int getSubError() {
    return this.subError;
  }
}
