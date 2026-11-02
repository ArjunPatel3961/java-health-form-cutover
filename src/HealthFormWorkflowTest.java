public final class HealthFormWorkflowTest {
    public static void main(String[] args) {
        check(HealthFormWorkflow.notificationFor(26) == HealthFormWorkflow.Notification.REMINDER, "26 hours should remind");
        check(HealthFormWorkflow.notificationFor(8) == HealthFormWorkflow.Notification.CONFIRMATION, "8 hours should confirm");
        System.out.println("HealthFormWorkflowTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
