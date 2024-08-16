<?php
if ($_SERVER["REQUEST_METHOD"] == "POST") {
    $name = $_POST["name"];
    $email = $_POST["email"];
    $message = $_POST["message"];

    $to = 'ronney@cs.washington.edu'; // Replace with your actual email address
    $subject = 'Contact Form Submission';
    $headers = "From: $email";
    $message_body = "Name: $name\nEmail: $email\nMessage:\n$message";

    // Send the email
    mail($to, $subject, $message_body, $headers);
}
?>
