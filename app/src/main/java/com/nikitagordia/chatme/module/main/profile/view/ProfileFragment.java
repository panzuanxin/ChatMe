package com.nikitagordia.chatme.module.main.profile.view;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.nikitagordia.chatme.R;
import com.nikitagordia.chatme.databinding.FragmentProfileBinding;
import com.nikitagordia.chatme.module.main.profile.model.BlogPost;
import com.nikitagordia.chatme.module.main.users.model.User;
import com.nikitagordia.chatme.module.signin.view.SigninActivity;

/**
 * Created by nikitagordia on 3/28/18.
 */

public class ProfileFragment extends Fragment {

    private FirebaseAuth auth;
    private FirebaseDatabase db;

    private FragmentProfileBinding bind;

    private ProgressDialog dialog;

    private ListAdapter adapter;
    private ChildEventListener childEventListener;

    private User user;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        bind = FragmentProfileBinding.inflate(inflater, container, false);

        auth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance();

        adapter = new ListAdapter(getContext(), getActivity(), bind.postList, new OnLikeCallback() {
            @Override
            public void onLike(final String postId) {
                db.getReference().child("post").child(postId).child("like_id").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot data : dataSnapshot.getChildren())
                            if (auth.getCurrentUser().getUid().equals((String)data.getValue())) return;
                        db.getReference().child("post").child(postId).child("like_id").push().setValue(auth.getCurrentUser().getUid());
                        db.getReference().child("post").child(postId).child("like").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                final long x = (long)dataSnapshot.getValue() + 1;
                                db.getReference().child("post").child(postId).child("like").setValue(x).addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        adapter.updateLike(postId, x);
                                    }
                                });
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
        });
        bind.postList.setLayoutManager(new LinearLayoutManager(getContext()));
        bind.postList.setAdapter(adapter);

        bind.addPost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (user == null) return;
                AlertDialog.Builder builder = new AlertDialog.Builder(ProfileFragment.this.getContext());
                View view = getLayoutInflater().inflate(R.layout.dialog_add_post, null);
                final EditText content = (EditText) view.findViewById(R.id.content);
                final TextView post = (TextView) view.findViewById(R.id.post);
                builder.setView(view);
                final AlertDialog d = builder.create();
                d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                d.show();
                post.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        d.cancel();
                        createPost(content.getText().toString());
                    }
                });
            }
        });

        dialog = new ProgressDialog(getContext());
        dialog.setMessage(getResources().getString(R.string.loading));
        dialog.setCancelable(false);
        dialog.show();

        db.getReference().child("user").child(auth.getCurrentUser().getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                user = dataSnapshot.getValue(User.class);
                user.setUid(auth.getCurrentUser().getUid());
                bind.userEmail.setText(user.getEmail());
                bind.userName.setText(user.getName());
                dialog.cancel();
                Toast.makeText(getContext(), getResources().getString(R.string.welcome), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                dialog.cancel();
                Toast.makeText(getContext(), getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
            }
        });

        String userEmail = "";
        if (auth.getCurrentUser().getEmail() != null) userEmail = auth.getCurrentUser().getEmail(); else userEmail = auth.getCurrentUser().getPhoneNumber();
        bind.userEmail.setText(userEmail);

        bind.logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                auth.signOut();
                startActivity(new Intent(getContext(), SigninActivity.class));
                getActivity().finish();
            }
        });

        return bind.getRoot();
    }

    void createPost(String content) {
        final String key = db.getReference().child("post").push().getKey();
        BlogPost post = new BlogPost(content, key, user.getUid(), user.getName());
        db.getReference().child("post").child(key).setValue(post);
        db.getReference().child("user").child(user.getUid()).child("post_id").push().setValue(key);
        db.getReference().child("user").child(user.getUid()).child("follower_id").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren())
                    db.getReference().child("user").child((String)snapshot.getValue()).child("post_id").push().setValue(key);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        update();
    }

    private void update() {
        if (childEventListener == null) {
            childEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    db.getReference().child("post").child((String)dataSnapshot.getValue()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            BlogPost post = dataSnapshot.getValue(BlogPost.class);
                            post.setId(dataSnapshot.getKey());
                            adapter.addPost(post);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            db.getReference().child("user").child(auth.getCurrentUser().getUid()).child("post_id").addChildEventListener(childEventListener);
        } else {
            adapter.clear();
            db.getReference().child("user").child(auth.getCurrentUser().getUid()).child("post_id").removeEventListener(childEventListener);
            db.getReference().child("user").child(auth.getCurrentUser().getUid()).child("post_id").addChildEventListener(childEventListener);
        }
    }

    public interface OnLikeCallback {
        void onLike(String postId);
    }
}
