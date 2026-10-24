"""Retry GPU batches after unwinding the failed inference stack."""


def encode_with_retry(batch, encode, oom_error, release_cache, on_split):
    try:
        encode(batch)
        return
    except oom_error:
        if len(batch) == 1:
            raise
    # Outside the handler: its traceback no longer retains the failed batch's GPU tensors.
    release_cache()
    midpoint = len(batch) // 2
    on_split(midpoint, len(batch) - midpoint)
    encode_with_retry(batch[:midpoint], encode, oom_error, release_cache, on_split)
    encode_with_retry(batch[midpoint:], encode, oom_error, release_cache, on_split)
