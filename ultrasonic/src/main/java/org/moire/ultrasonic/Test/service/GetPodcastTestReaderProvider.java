package org.moire.ultrasonic.Test.service;

import java.io.Reader;
import java.io.StringReader;

/**
 * Created by rcocula on 11/03/2016.
 */
public class GetPodcastTestReaderProvider {

    private static String data = "<subsonic-response status=\"ok\" version=\"1.12.0\" xmlns=\"http://subsonic.org/restapi\">\n" +
            "   <podcasts>\n" +
            "      <channel id=\"0\" url=\"http://radiofrance-podcast.net/podcast09/rss_13183.xml\" title=\"La tribune des critiques de disques\" description=\"Sous la houlette de Jérémie Rousseau, d'éminents critiques musicaux écoutent à l'aveugle différentes versions d'une oeuvre du répertoire et la commentent\" status=\"completed\"/>\n" +
            "      <channel id=\"1\" url=\"http://radiofrance-podcast.net/podcast09/rss_11874.xml\" title=\"La Matinale du samedi\" description=\"Une version détendue pour les matinaux du week end\" status=\"completed\"/>\n" +
            "      <channel id=\"2\" url=\"http://radiofrance-podcast.net/podcast09/rss_10467.xml\" title=\"LES NOUVEAUX CHEMINS DE LA CONNAISSANCE\" description=\"Une rencontre quotidienne entre philosophie et monde contemporain.\" status=\"completed\"/>\n" +
            "      <channel id=\"3\" url=\"http://radiofrance-podcast.net/podcast09/rss_12087.xml\" title=\"Alla Breve, l'intégrale\" description=\"Une oeuvre courte, commandée à un compositeur d aujourd hui, diffusée en 5 mouvements durant la semaine et proposée en podcast dans son intégralité.\" status=\"completed\"/>\n" +
            "      <channel id=\"4\" url=\"http://lescastcodeurs.libsyn.com/rss\" title=\"Les Cast Codeurs Podcast\" description=\"Le podcast Java en Français dans le texte\" status=\"completed\"/>\n" +
            "      <channel id=\"6\" url=\"http://radiofrance-podcast.net/podcast09/rss_14003.xml\" title=\"Le cri du patchwork\" description=\"Si le patchwork était un animal, quel serait son cri ?\" status=\"completed\"/>\n" +
            "      <channel id=\"7\" url=\"http://radiofrance-podcast.net/podcast09/rss_12289.xml\" title=\"Electromania\" description=\"Electromania continue de témoigner de toutes les musiques avant tout inventives et inclassables, de Pierre Henry à Nick Cave.\" status=\"completed\"/>\n" +
            "      <channel id=\"8\" url=\"http://radiofrance-podcast.net/podcast09/rss_11910.xml\" title=\"CONTINENT MUSIQUE\" description=\"Funk, baroque, jazz, électro, classique, chanson, musique concrète ou hip-hop abstrait...\" status=\"completed\"/>\n" +
            "      <channel id=\"9\" url=\"http://radiofrance-podcast.net/podcast09/rss_11985.xml\" title=\"SUPERSONIC\" description=\"Un homme ou une femme de son fait partager ses créations et son univers\" status=\"completed\"/>\n" +
            "      <channel id=\"10\" url=\"http://radiofrance-podcast.net/podcast09/rss_12668.xml\" title=\"Label pop\" description=\"Chaque semaine, une oreille attentive à l'actualité, pour restituer l'éclatante vitalité de la pop moderne, entendue au sens le plus large\" status=\"completed\"/>\n" +
            "      <channel id=\"11\" url=\"http://radiofrance-podcast.net/podcast09/rss_11224.xml\" title=\"Le magazine de la contemporaine\" description=\"Interviews et reportages autour de l'actualité de la musique contemporaine\" status=\"completed\"/>\n" +
            "      <channel id=\"12\" url=\"http://radiofrance-podcast.net/podcast09/rss_11393.xml\" title=\"Les greniers de la mémoire\" description=\"Une visite complice et nostalgique des archives musicales de Radio France\" status=\"completed\"/>\n" +
            "      <channel id=\"13\" url=\"http://radiofrance-podcast.net/podcast09/rss_14498.xml\" title=\"Musicopolis\" description=\"Une ville, un compositeur, une époque. Une histoire de la musique racontée chaque semaine.\" status=\"completed\"/>\n" +
            "      <channel id=\"14\" url=\"http://radiofrance-podcast.net/podcast09/rss_14603.xml\" title=\"On ne peut pas tout savoir\" description=\"Parce qu'on ne peut pas tout savoir, Arnaud Merlin vous propose chaque semaine un voyage dans un univers musical différent.\" status=\"completed\"/>\n" +
            "      <channel id=\"15\" url=\"http://radiofrance-podcast.net/podcast09/rss_12576.xml\" title=\"LES CARNETS DE L'ECONOMIE\" description=\"Un chercheur ou un acteur de la sphère économique et sociale nous livre un concentré de ses travaux et de sa réflexion\" status=\"completed\"/>\n" +
            "      <channel id=\"16\" url=\"http://radiofrance-podcast.net/podcast09/rss_14076.xml\" title=\"LES CARNETS DE LA CREATION\" description=\"LES CARNETS DE LA CREATION\" status=\"completed\"/>\n" +
            "      <channel id=\"18\" url=\"http://radiofrance-podcast.net/podcast09/rss_14663.xml\" title=\"LE MONDE SELON XAVIER DELAPORTE\" description=\"LE MONDE SELON XAVIER DELAPORTE\" status=\"completed\"/>\n" +
            "      <channel id=\"19\" url=\"http://radiofrance-podcast.net/podcast09/rss_16260.xml\" title=\"LA SUITE DANS LES IDEES\" description=\"Contribuer à alimenter le débat public par les idées\" status=\"completed\"/>\n" +
            "      <channel id=\"20\" url=\"http://radiofrance-podcast.net/podcast09/rss_13959.xml\" title=\"CULTURE MUSIQUE\" description=\"CULTURE MUSIQUE\" status=\"completed\"/>\n" +
            "      <channel id=\"21\" url=\"http://radiofrance-podcast.net/podcast09/rss_14009.xml\" title=\"Carnets de voyages\" description=\"Carnet de voyage est un atlas ouvert sur les musiques que l'on dit de tradition orale ou extra-européennes.\" status=\"completed\"/>\n" +
            "   </podcasts>\n" +
            "</subsonic-response>\n";


    public static Reader getReader() {

        return new StringReader(data);
    }
}
