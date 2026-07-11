import {failureReasonDescription} from './index';

describe('Gremlin execution history failure reason', () => {
    const t = key => ({
        'analysis.logs.failure_reason.GREMLIN_EXECUTION_FAILED':
            'Query failed. Review the statement and try again.',
    })[key];

    test('maps a controlled reason code to actionable localized text', () => {
        expect(failureReasonDescription(
            {status: 'FAILED', failure_reason: 'GREMLIN_EXECUTION_FAILED'}, t
        )).toBe('Query failed. Review the statement and try again.');
    });

    test('does not show a reason for successful history', () => {
        expect(failureReasonDescription(
            {status: 'SUCCESS', failure_reason: 'GREMLIN_EXECUTION_FAILED'}, t
        )).toBeNull();
    });

    test('does not expose unknown backend values', () => {
        expect(failureReasonDescription(
            {status: 'FAILED', failure_reason: 'raw Groovy signature'}, t
        )).toBeNull();
    });
});
