package org.openaudible.feeds.pagebuilder;

import com.google.gson.Gson;
import org.eclipse.jetty.util.IO;
import org.openaudible.Audible;
import org.openaudible.BookToFilenameStrategy;
import org.openaudible.books.Book;
import org.openaudible.books.BookElement;
import org.openaudible.progress.IProgressTask;
import org.openaudible.util.CopyWithProgress;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebPage {
    final File webDir;
    IProgressTask progress;
    int thumbSize = 200; // If changed, need to change index.html

    public WebPage(File dir) {
        webDir = dir;
    }

    BookInfo toBookInfo(Book b) {
        BookInfo i = new BookInfo();
        i.title = b.get(BookElement.fullTitle);
        i.author = b.get(BookElement.author);
        i.narratedBy = b.get(BookElement.narratedBy);
        i.summary = b.get(BookElement.summary);
        i.run_time = b.get(BookElement.duration);
        i.rating_average = b.get(BookElement.rating_average);
        i.rating_count = b.get(BookElement.rating_count);
        i.audible = b.get(BookElement.infoLink);
        i.description = b.get(BookElement.description);
        i.purchased = b.get(BookElement.purchase_date);
        return i;
    }

    public void subtask(Book b, String s) throws Exception {
        if (progress != null) {
            String n = b.getShortTitle();
            if (n.length() > 32)
                n = n.substring(0, 28) + "...";
            progress.setSubTask(s + " " + n);
            if (progress.wasCanceled())
                throw new Exception("User canceled");

        }
    }

    public void buildPage(List<Book> books) throws Exception {

        File mp3Dir = new File(webDir, "mp3");
        File coverImages = new File(webDir, "cover");
        File thumbImages = new File(webDir, "thumb");

        if (!mp3Dir.exists())
            mp3Dir.mkdirs();
        if (!coverImages.exists())
            coverImages.mkdirs();
        if (!thumbImages.exists())
            thumbImages.mkdirs();

        Gson gson = new Gson();
        ArrayList<BookInfo> list = new ArrayList<BookInfo>();

        ArrayList<Book> toCopy = new ArrayList<Book>();
        for (Book b : books) {
            File mp3 = Audible.instance.getMP3FileDest(b);
            if (!mp3.exists())
                continue;
            String fileName = getFileName(b); // human readable, without extension.
            String mp3Name = fileName + ".mp3";
            File mp3File = new File(mp3Dir, mp3Name);

            if (!mp3File.exists() || mp3File.length() != mp3.length()) {
                toCopy.add(b);
            }
        }
        if (toCopy.size() > 0) {
            if (progress != null)
                progress.setTask("Copying MP3s to Web Page Directory", "");
            int count = 1;
            for (Book b : toCopy) {
                if (progress.wasCanceled())
                    throw new Exception("Canceled");

                progress.setTask("Copying " + count + " of " + toCopy.size() + " book to web's MP3 directory");
                File mp3 = Audible.instance.getMP3FileDest(b);
                String fileName = getFileName(b); // human readable, without extension.
                String mp3Name = fileName + ".mp3";
                File mp3File = new File(mp3Dir, mp3Name);

                CopyWithProgress.copyWithProgress(progress, mp3, mp3File);

                count++;
            }

        }

        if (progress != null) {
            progress.setTask("Creating Book Web Page", "");
        }

        for (Book b : books) {
            File mp3 = Audible.instance.getMP3FileDest(b);

            subtask(b, "Reading");

            // only export mp3
            if (!mp3.exists())
                continue;

            File img = Audible.instance.getImageFileDest(b);
            String fileName = getFileName(b); // human readable, without extension.

            String coverName = fileName + ".jpg";
            String thumbName = fileName + ".jpg";
            String mp3Name = fileName + ".mp3";

            File coverFile = new File(coverImages, coverName);
            File thumbFile = new File(thumbImages, thumbName);
            File mp3File = new File(mp3Dir, mp3Name);

            BookInfo i = toBookInfo(b);
            i.mp3 = mp3Name;

            if (img.exists()) {
                if (!coverFile.exists() || coverFile.length() != img.length()) {
                    subtask(b, "Copying image");
                    IO.copy(img, coverFile);
                }

                if (!thumbFile.exists()) {
                    subtask(b, "Creating thumbnail");
                    createThumbnail(img, thumbFile, thumbSize);
                }

                i.image = coverName;
            } else
                i.image = "";

            list.add(i);

        }

        if (progress != null) {
            if (progress.wasCanceled())
                throw new Exception("User canceled");
            progress.setTask(null, "Exporting web data");
        }

        String json = gson.toJson(list);
        FileWriter writer = null;

        try {
            writer = new FileWriter(new File(webDir, "index.json"));
            writer.write(json);
            writer.close();

            writer = new FileWriter(new File(webDir, "books.js"));
            writer.write("window.myBooks=");
            writer.write(json);
            writer.write(";");
        } finally {
            if (writer != null)
                writer.close();
        }

    }

    private String getFileName(Book b) {
        String s = BookToFilenameStrategy.instance.getReadableFileName(b);
        return s;
    }

    private void createThumbnail(File srcFile, File destFile, int siz) throws IOException {
        BufferedImage img = new BufferedImage(siz, siz, BufferedImage.TYPE_INT_RGB);
        img.createGraphics().drawImage(ImageIO.read(srcFile).getScaledInstance(siz, siz, Image.SCALE_SMOOTH), 0, 0, null);
        ImageIO.write(img, "jpg", destFile);
    }

    public void setProgress(IProgressTask progressTask) {
        progress = progressTask;
    }

}