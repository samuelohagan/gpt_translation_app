package com.example.translationapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

class WordsDialogFragment extends DialogFragment {
    private List<String> words;

    public WordsDialogFragment(List<String> words) {
        this.words = words;
    }

    @Nullable
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.fragment_scrolling, null);

        //Set up the recycler view
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(new WordsAdapter(words));

        Button exitButton = view.findViewById(R.id.exit_button);
        exitButton.setOnClickListener(v -> dismiss());

        builder.setView(view);
        return builder.create();
    }

    private class WordsAdapter extends RecyclerView.Adapter<WordsAdapter.WordViewHolder> {

        private List<String> words;

        public WordsAdapter(List<String> words) {
            this.words = words;
        }

        @NonNull
        @Override
        public WordsAdapter.WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new WordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WordsAdapter.WordViewHolder holder, int position) {
            holder.wordTextView.setText(words.get(position));
        }

        @Override
        public int getItemCount() {
            return words.size();
        }
        class WordViewHolder extends RecyclerView.ViewHolder {
            TextView wordTextView;
            public WordViewHolder(@NonNull View itemView) {
                super(itemView);
                wordTextView = itemView.findViewById(android.R.id.text1);
            }
        }

    }


}